package store.mailstock.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import store.mailstock.auth.entity.User;
import store.mailstock.common.exception.ApiException;
import store.mailstock.setting.entity.Setting;
import store.mailstock.setting.repo.SettingRepository;
import store.mailstock.submission.entity.AccountCategory;

/**
 * MailStock Telegram bot. Buyers/sellers browse & buy, manage wallet/deposits and sell accounts —
 * every action goes through {@link BotApiClient} (the real REST API) as the linked user.
 *
 * UX: fully button-driven. Every screen carries navigation (Menu / Back / Cancel), every flow can be
 * cancelled, and every failure (wrong password, invalid/expired code, unknown command, not connected,
 * insufficient funds, validation) replies with a clear reason plus a way forward.
 */
@Slf4j
public class MailStockBot extends TelegramLongPollingBot {

    private static final String PAY_ID_KEY = "deposit.binance_pay_id";
    private static final String QR_NAME = "deposit-qr";
    private static final String BUNDLED_QR = "images/binance qr.jpg";

    private final String username;
    private final TelegramLinkService links;
    private final BotApiClient api;
    private final SettingRepository settings;
    private final store.mailstock.inventory.service.PricingService pricing;
    private final store.mailstock.media.MediaService media;

    private enum Step { NONE, LINK_CODE, DEPOSIT_AMOUNT, DEPOSIT_TXID,
        SELL_EMAIL, SELL_PASSWORD, SELL_COUNTRY, SELL_2FA,
        TICKET_SUBJECT, TICKET_BODY, WARRANTY_DESC, CART_COUPON }

    /** Public website used in guidance/links. Overridable via the {@code site.url} setting. */
    private static final String DEFAULT_SITE_URL = "https://mailstock.store";

    /** Buy-many cap per checkout (mirrors the website cart). */
    private static final int CART_MAX = 50;

    private static final class Session {
        Step step = Step.NONE;
        final Map<String, Object> data = new ConcurrentHashMap<>();
        // Cart survives flow cancellations (resetConv only clears step+data), so items stay while browsing.
        final List<Long> cart = new ArrayList<>();
        final Map<Long, String> cartLabels = new ConcurrentHashMap<>();
        String coupon;
    }
    private final ConcurrentHashMap<Long, Session> sessions = new ConcurrentHashMap<>();

    public MailStockBot(String botToken, String username, TelegramLinkService links, BotApiClient api,
                        SettingRepository settings, store.mailstock.inventory.service.PricingService pricing,
                        store.mailstock.media.MediaService media) {
        super(botToken);
        this.username = username;
        this.links = links;
        this.api = api;
        this.settings = settings;
        this.pricing = pricing;
        this.media = media;
    }

    @Override public String getBotUsername() { return username; }

    /** Push an unsolicited message to a chat (used by {@link TelegramNotifier}). */
    public void pushMessage(Long chatId, String markdown) { send(chatId, markdown); }

    @Override
    public void onUpdateReceived(Update update) {
        Long chatId = chatIdOf(update);
        try {
            if (update.hasCallbackQuery()) onCallback(update.getCallbackQuery());
            else if (update.hasMessage() && update.getMessage().hasText())
                onText(chatId, update.getMessage().getText().trim(), update.getMessage().getMessageId());
            else if (chatId != null)
                send(chatId, "I can only read text and button taps. Open the menu below.", menuOnly());
        } catch (ApiException | BotApiClient.BotApiException e) {
            // Expected, user-facing failures (bad password, invalid/expired code, insufficient funds…)
            if (chatId != null) send(chatId, "❌ " + e.getMessage(), menuOnly());
        } catch (Exception e) {
            log.warn("[TELEGRAM] update handling failed: {}", e.toString());
            if (chatId != null) send(chatId, "⚠️ Something went wrong. Please try again.", menuOnly());
        }
    }

    // ---------- text / commands ----------
    private void onText(Long chatId, String text, Integer messageId) {
        Session s = sessions.computeIfAbsent(chatId, k -> new Session());

        // Mid-conversation input takes priority (except explicit /cancel or /menu).
        if (s.step != Step.NONE && !text.equalsIgnoreCase("/cancel") && !text.equalsIgnoreCase("/menu")) {
            handleConversation(chatId, s, text, messageId);
            return;
        }

        String cmd = text.split("\\s+")[0].toLowerCase();
        String arg = text.contains(" ") ? text.substring(text.indexOf(' ') + 1).trim() : "";

        // Gate: the bot is unusable until the chat is linked to an account. Only connection/help/nav
        // commands work before that — everything else replies with clear connect-first instructions.
        boolean open = switch (cmd) {
            case "/start", "/menu", "/help", "/guide", "/howto", "/link", "/cancel" -> true;
            default -> false;
        };
        if (!open && links.resolveUser(chatId).isEmpty()) { promptConnect(chatId); return; }

        switch (cmd) {
            case "/start" -> { if (!arg.isBlank()) doLinkCode(chatId, arg); else sendMenu(chatId); }
            case "/menu" -> sendMenu(chatId);
            case "/help", "/guide", "/howto" -> showHelp(chatId);
            case "/link" -> { if (arg.isBlank()) startLinkCode(chatId, s); else doLinkCode(chatId, arg); }
            case "/logout" -> doLogout(chatId, s);
            case "/browse" -> showCategories(chatId);
            case "/wallet" -> showWallet(chatId);
            case "/deposit" -> startDeposit(chatId, s);
            case "/sell" -> startSell(chatId, s);
            case "/submissions" -> showSubmissions(chatId);
            case "/orders" -> showOrders(chatId);
            case "/myemails" -> showMyEmails(chatId);
            case "/cart" -> showCart(chatId, s);
            case "/support", "/ticket" -> startTicket(chatId, s);
            case "/warranty" -> startWarranty(chatId);
            case "/profile", "/me" -> showProfile(chatId);
            case "/cancel" -> { resetConv(s); send(chatId, "Cancelled.", menuOnly()); }
            default -> send(chatId,
                    "🤔 I didn't understand *" + safe(text) + "*.\nUse the menu below or type /menu.",
                    menuOnly());
        }
    }

    private void handleConversation(Long chatId, Session s, String text, Integer messageId) {
        switch (s.step) {
            case LINK_CODE -> { doLinkCode(chatId, extractLinkCode(text)); resetConv(s); }
            case DEPOSIT_AMOUNT -> {
                BigDecimal amt = parseAmount(text);
                if (amt == null) { send(chatId, "Enter a valid amount, e.g. `25`.", cancelKb()); return; }
                s.data.put("amount", amt); s.step = Step.DEPOSIT_TXID;
                send(chatId, "Now send the *Binance Pay transaction ID (txid)* of your transfer:", cancelKb());
            }
            case DEPOSIT_TXID -> {
                User u = requireUser(chatId); if (u == null) { resetConv(s); return; }
                BigDecimal amt = (BigDecimal) s.data.get("amount");
                resetConv(s);
                JsonNode d = api.deposit(u, amt, text);
                String status = d.path("status").asText("PENDING");
                send(chatId, "APPROVED".equals(status)
                        ? "✅ Deposit *credited* instantly!"
                        : "🕓 Deposit #" + d.path("id").asText("?") + " submitted (" + status + "). It'll credit once confirmed.",
                        rows(List.of(btn("💰 Wallet", "act:wallet")), List.of(btn("🏠 Menu", "act:menu"))));
            }
            case SELL_EMAIL -> {
                if (!text.contains("@")) { send(chatId, "Enter a valid *email address*:", cancelKb()); return; }
                s.data.put("emailAddress", text); s.step = Step.SELL_PASSWORD;
                send(chatId, "Account *password*:", cancelKb());
            }
            case SELL_PASSWORD -> {
                deleteMessage(chatId, messageId); s.data.put("emailPassword", text);
                s.step = Step.SELL_COUNTRY; send(chatId, "*Country* of the account (e.g. US):", cancelKb());
            }
            case SELL_COUNTRY -> {
                s.data.put("country", text);
                AccountCategory cat = categoryOf(s);
                // 2FA categories need a TOTP secret (the API rejects the submission otherwise);
                // no-2FA categories go straight to submission. Price is never asked — it's fixed by category.
                if (cat != null && cat.requires2FA) {
                    s.step = Step.SELL_2FA;
                    send(chatId, "This category needs *2FA*. Send the account's *2FA / TOTP secret key* "
                            + "(the authenticator setup key, not a 6-digit code):", cancelKb());
                } else {
                    submitSell(chatId, s);
                }
            }
            case SELL_2FA -> {
                s.data.put("twoFactorCode", text.trim());
                submitSell(chatId, s);
            }
            case TICKET_SUBJECT -> {
                if (text.length() < 3) { send(chatId, "Please enter a short *subject* (min 3 characters):", cancelKb()); return; }
                s.data.put("subject", text.length() > 200 ? text.substring(0, 200) : text);
                s.step = Step.TICKET_BODY;
                send(chatId, "Now describe your issue in a *message* (what happened, order # if any):", cancelKb());
            }
            case TICKET_BODY -> {
                if (text.length() < 5) { send(chatId, "Please add a little more detail (min 5 characters):", cancelKb()); return; }
                User u = requireUser(chatId); if (u == null) { resetConv(s); return; }
                Map<String, Object> body = new java.util.HashMap<>();
                body.put("subject", s.data.get("subject"));
                body.put("category", "General");
                body.put("body", text.length() > 10000 ? text.substring(0, 10000) : text);
                resetConv(s);
                JsonNode d = api.createTicket(u, body);
                send(chatId, "✅ *Ticket #" + d.path("id").asText("?") + " opened.*\n"
                        + "Our team will reply soon — you'll get a message here and can also read replies on the website.",
                        rows(List.of(btn("🏠 Menu", "act:menu"))));
            }
            case CART_COUPON -> {
                String code = text.trim();
                resetConv(s);
                s.coupon = code.isBlank() ? null : code;
                showCart(chatId, s); // re-quotes and shows whether the coupon applied
            }
            case WARRANTY_DESC -> {
                if (text.length() < 5) { send(chatId, "Please describe the problem in a bit more detail (min 5 characters):", cancelKb()); return; }
                User u = requireUser(chatId); if (u == null) { resetConv(s); return; }
                Object oid = s.data.get("orderItemId");
                if (oid == null) { resetConv(s); send(chatId, "That warranty session expired. Start again from 🛡 Warranty.", menuOnly()); return; }
                Map<String, Object> body = new java.util.HashMap<>();
                body.put("orderItemId", oid);
                body.put("reason", text.length() > 60 ? text.substring(0, 60) : text);
                body.put("description", text.length() > 5000 ? text.substring(0, 5000) : text);
                resetConv(s);
                JsonNode d = api.openWarranty(u, body);
                send(chatId, "✅ *Warranty claim #" + d.path("id").asText("?") + " submitted.*\n"
                        + "An admin will review it and either replace the account or refund you. You'll be notified here.",
                        rows(List.of(btn("📧 My Emails", "act:emails")), List.of(btn("🏠 Menu", "act:menu"))));
            }
            default -> resetConv(s);
        }
    }

    // ---------- callbacks (inline buttons) ----------
    private void onCallback(CallbackQuery cq) {
        Long chatId = cq.getMessage().getChatId();
        String data = cq.getData();
        ack(cq.getId());
        Session s = sessions.computeIfAbsent(chatId, k -> new Session());

        // Gate: block every action for an unconnected chat except navigation/help/connect taps.
        if (!isOpenAction(data) && links.resolveUser(chatId).isEmpty()) { promptConnect(chatId); return; }

        // Any navigation/menu tap cancels an in-progress flow (context switch).
        if (data.startsWith("act:")) {
            resetConv(s);
            switch (data.substring(4)) {
                case "menu", "home" -> sendMenu(chatId);
                case "browse" -> showCategories(chatId);
                case "wallet" -> showWallet(chatId);
                case "deposit" -> startDeposit(chatId, s);
                case "orders" -> showOrders(chatId);
                case "emails" -> showMyEmails(chatId);
                case "sell" -> startSell(chatId, s);
                case "submissions" -> showSubmissions(chatId);
                case "help", "guide" -> showHelp(chatId);
                case "support" -> startTicket(chatId, s);
                case "warranty" -> startWarranty(chatId);
                case "profile" -> showProfile(chatId);
                case "cart" -> showCart(chatId, s);
                case "checkout" -> checkout(chatId, s);
                case "cartclear" -> clearCart(chatId, s);
                case "coupon" -> startCoupon(chatId, s);
                case "linkcode" -> startLinkCode(chatId, s);
                case "logout" -> doLogout(chatId, s);
                case "cancel" -> send(chatId, "Cancelled.", menuOnly());
                default -> sendMenu(chatId);
            }
            return;
        }
        if (data.startsWith("cat:")) {                       // browse a category (optional :page suffix)
            String rest = data.substring(4);
            int sep = rest.lastIndexOf(':');
            if (sep > 0) showCategory(chatId, rest.substring(0, sep), parseInt(rest.substring(sep + 1)));
            else showCategory(chatId, rest, 0);
        }
        else if (data.startsWith("item:")) showItem(chatId, parseLong(data.substring(5)));
        else if (data.startsWith("buyc:")) confirmBuy(chatId, parseLong(data.substring(5)));
        else if (data.startsWith("buy:")) doBuy(chatId, parseLong(data.substring(4)));
        else if (data.startsWith("wsel:")) pickWarrantyItem(chatId, s, parseLong(data.substring(5)));
        else if (data.startsWith("scat:")) startSellDetails(chatId, s, data.substring(5));
        else if (data.startsWith("addc:")) addToCart(chatId, s, parseLong(data.substring(5)));
        else if (data.startsWith("crm:")) removeFromCart(chatId, s, parseLong(data.substring(4)));
        else send(chatId, "That button is no longer valid.", menuOnly());
    }

    // ---------- flows ----------
    /** Actions usable BEFORE connecting: navigation, help, and the connect flow itself. */
    private static boolean isOpenAction(String data) {
        if (!data.startsWith("act:")) return false; // cat:/item:/buy:/… always need a connected account
        return switch (data.substring(4)) {
            case "menu", "home", "help", "guide", "linkcode", "cancel" -> true;
            default -> false;
        };
    }

    /** Clear connect-first instructions, shown whenever an unconnected chat tries to do anything. */
    private void promptConnect(Long chatId) {
        String site = siteUrl();
        send(chatId, "🔒 *Connect your account first*\n\n"
                + "This bot works once you link it to your MailStock account. It takes a few seconds:\n\n"
                + "1️⃣ Create a free account (or log in) on the website.\n"
                + "2️⃣ Open *Profile → Connect Telegram*.\n"
                + "3️⃣ Tap *Open bot* / scan the QR — or copy the code and tap *🔗 Connect with code* below and send it here.\n\n"
                + "Once connected, you'll get a menu tailored to your account (buyer or seller).",
                rows(List.of(urlBtn("🌐 Create account / log in", site + "/register")),
                        List.of(btn("🔗 Connect with code", "act:linkcode"), btn("❓ How it works", "act:help"))));
    }

    private void startLinkCode(Long chatId, Session s) {
        s.step = Step.LINK_CODE; s.data.clear();
        send(chatId, "🔗 *Connect with a code*\n\nOn the website open *Profile → Connect Telegram* — from there you can tap "
                + "*Open bot* (connects instantly) or *scan the QR*, or copy the code and send it here.\n\n"
                + "Don't have an account yet? Create one first.",
                rows(List.of(urlBtn("🌐 Open website", siteUrl())), List.of(btn("✖️ Cancel", "act:cancel"))));
    }

    private void doLinkCode(Long chatId, String code) {
        Optional<User> already = links.resolveUser(chatId);
        try {
            User u = links.consumeCode(code, chatId); // throws ApiException with a clear reason on failure
            boolean switched = already.isPresent() && !already.get().getId().equals(u.getId());
            send(chatId, "✅ Connected as *" + safe(u.getEmail()) + "*."
                    + (switched ? "\n_Switched from " + safe(already.get().getEmail()) + "._" : ""));
            sendMenu(chatId);
        } catch (ApiException e) {
            // Opening the deep link again (code already consumed) shouldn't look like a failure when the
            // chat is already linked — just reassure. A genuine first-time failure still surfaces normally.
            if (already.isPresent()) {
                send(chatId, "✅ You're already connected as *" + safe(already.get().getEmail()) + "*.");
                sendMenu(chatId);
            } else {
                throw e;
            }
        }
    }

    private void doLogout(Long chatId, Session s) {
        boolean was = links.resolveUser(chatId).isPresent();
        links.unlink(chatId); resetConv(s);
        s.cart.clear(); s.cartLabels.clear(); s.coupon = null; // don't carry a cart across accounts
        send(chatId, was ? "🚪 Disconnected. Use Login to reconnect." : "You weren't connected.", menuOnly());
    }

    /** Step 1 of buying: pick a category. Buyers choose the account age / 2FA profile they want. */
    private void showCategories(Long chatId) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        // Only show categories that are actually on sale (a positive sell price for some provider).
        for (store.mailstock.submission.entity.AccountCategory c : store.mailstock.submission.entity.AccountCategory.values())
            if (pricing.isSellable(store.mailstock.submission.entity.SellerSubmission.Provider.GMAIL, c)
                    || pricing.isSellable(store.mailstock.submission.entity.SellerSubmission.Provider.OUTLOOK, c))
                rows.add(List.of(btn(c.label, "cat:" + c.name())));
        rows.add(List.of(btn("🏠 Menu", "act:menu")));
        if (rows.size() == 1) {
            send(chatId, "*🛍 Browse & Buy*\n\n😔 No accounts are on sale right now. Please check back soon.",
                    rows(List.of(btn("🏠 Menu", "act:menu"))));
            return;
        }
        send(chatId, "*🛍 Browse & Buy*\n\nChoose the type of account you're looking for:", rows);
    }

    /** Step 2: show availability for the chosen category and let the buyer pick one to purchase. */
    private void showCategory(Long chatId, String catKey, int page) {
        String label = catKey;
        try { label = store.mailstock.submission.entity.AccountCategory.valueOf(catKey).label; } catch (RuntimeException ignored) {}

        JsonNode d = api.browseCategory(catKey, page);
        JsonNode content = d.path("content");
        List<List<InlineKeyboardButton>> back = rows(
                List.of(btn("⬅️ Categories", "act:browse")), List.of(btn("🏠 Menu", "act:menu")));
        if (!content.isArray() || content.isEmpty()) {
            send(chatId, "*" + safe(label) + "*\n\n😔 No accounts are available in this category right now.\n"
                    + "Please check back soon, or pick another category.", back);
            return;
        }
        long total = d.path("totalElements").asLong(content.size());
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        StringBuilder sb = new StringBuilder("*" + safe(label) + "*\n✅ " + total + " in stock\n\n");
        for (JsonNode it : content) {
            long id = it.path("id").asLong();
            String price = it.path("sellingPrice").asText("?");
            sb.append("• ").append(it.path("provider").asText(""))
              .append(" · ").append(it.path("country").asText(""))
              .append(" · Warranty ").append(it.path("warrantyDays").asText("0")).append("d")
              .append(" — *$").append(price).append("*\n");
            rows.add(List.of(btn("🛒 Buy · $" + price, "buyc:" + id), btn("➕ Cart", "addc:" + id), btn("🔍", "item:" + id)));
        }
        int totalPages = d.path("totalPages").asInt(1);
        List<InlineKeyboardButton> nav = new ArrayList<>();
        if (page > 0) nav.add(btn("⬅️ Prev", "cat:" + catKey + ":" + (page - 1)));
        if (page + 1 < totalPages) nav.add(btn("Next ➡️", "cat:" + catKey + ":" + (page + 1)));
        if (!nav.isEmpty()) rows.add(nav);
        rows.add(List.of(btn("🛒 View cart", "act:cart"), btn("⬅️ Categories", "act:browse")));
        rows.add(List.of(btn("🏠 Menu", "act:menu")));
        send(chatId, sb.toString(), rows);
    }

    private void showItem(Long chatId, long id) {
        JsonNode it = api.item(id);
        String cat = it.path("accountCategoryLabel").asText("");
        String catKey = it.path("accountCategory").asText("");
        String txt = "*🔍 Account details*\n\n"
                + "Provider: *" + it.path("provider").asText("") + "*\n"
                + (cat.isBlank() ? "" : "Category: " + safe(cat) + "\n")
                + "Country: " + it.path("country").asText("") + "\n"
                + "Warranty: " + it.path("warrantyDays").asText("0") + " days\n"
                + "Price: *$" + it.path("sellingPrice").asText("?") + "*\n\n"
                + safe(it.path("description").asText(""))
                + "\n\n_The full email address is revealed only after purchase._";
        send(chatId, txt, rows(
                List.of(btn("🛒 Buy now", "buyc:" + id), btn("➕ Add to cart", "addc:" + id)),
                List.of(btn("⬅️ Back", catKey.isBlank() ? "act:browse" : "cat:" + catKey), btn("🏠 Menu", "act:menu"))));
    }

    /** Step 3: confirmation screen — shows price, warranty and the buyer's balance before charging. */
    private void confirmBuy(Long chatId, long id) {
        User u = requireUser(chatId); if (u == null) return;
        JsonNode it = api.item(id);
        String cat = it.path("accountCategoryLabel").asText("");
        String catKey = it.path("accountCategory").asText("");
        String price = it.path("sellingPrice").asText("?");
        String bal = api.wallet(u).path("availableBalance").asText("0");
        boolean enough = true;
        try { enough = new BigDecimal(bal).compareTo(new BigDecimal(price)) >= 0; } catch (RuntimeException ignored) {}

        String txt = "*🧾 Confirm your purchase*\n\n"
                + "Provider: *" + it.path("provider").asText("") + "*\n"
                + (cat.isBlank() ? "" : "Category: " + safe(cat) + "\n")
                + "Country: " + it.path("country").asText("") + "\n"
                + "Warranty: " + it.path("warrantyDays").asText("0") + " days\n"
                + "Price: *$" + price + "*\n"
                + "Your balance: $" + bal + "\n\n"
                + (enough ? "Tap confirm to pay from your balance. The login details appear instantly."
                          : "⚠️ Your balance is too low. Add funds, then come back to buy.");
        List<InlineKeyboardButton> backRow = List.of(
                btn("⬅️ Back", catKey.isBlank() ? "act:browse" : "cat:" + catKey), btn("🏠 Menu", "act:menu"));
        send(chatId, txt, enough
                ? rows(List.of(btn("✅ Confirm & pay $" + price, "buy:" + id)), backRow)
                : rows(List.of(btn("➕ Add balance", "act:deposit")), backRow));
    }

    /** Final step (single item): charge the balance and deliver the credentials right in the chat. */
    private void doBuy(Long chatId, long id) {
        User u = requireUser(chatId); if (u == null) return;
        sendOrderDelivery(chatId, api.buy(u, List.of(id), null));
    }

    /** Shared post-purchase delivery: confirm payment then dump each account's credentials. */
    private void sendOrderDelivery(Long chatId, JsonNode order) {
        JsonNode items = order.path("items");
        send(chatId, "✅ *Payment confirmed* — $" + order.path("totalAmount").asText("?")
                + " charged from your balance.\n\n📧 *Your login details:*");
        if (items.isArray() && !items.isEmpty()) {
            for (JsonNode it : items) {
                String creds = it.path("deliveryPayload").asText("");
                send(chatId, "```\n" + (creds.isBlank() ? "Credentials will appear under My Emails shortly." : creds) + "\n```");
            }
        }
        send(chatId, "_Keep these safe — you can view them again any time under 📧 My Emails._",
                rows(List.of(btn("📧 My Emails", "act:emails")),
                        List.of(btn("🛍 Browse more", "act:browse"), btn("🏠 Menu", "act:menu"))));
    }

    /** In-bot profile card: who you're connected as (name, email, role, balance) without leaving Telegram. */
    private void showProfile(Long chatId) {
        User u = requireUser(chatId); if (u == null) return;
        String roles = u.getRoles().isEmpty() ? "—"
                : u.getRoles().stream().map(r -> {
                    String n = r.name();
                    return n.charAt(0) + n.substring(1).toLowerCase();
                }).collect(java.util.stream.Collectors.joining(", "));
        StringBuilder sb = new StringBuilder("*👤 Your profile*\n\n");
        sb.append("Name: *").append(safe(blankDash(u.getFullName()))).append("*\n");
        sb.append("Email: `").append(safe(u.getEmail())).append("`\n");
        if (u.getPhone() != null && !u.getPhone().isBlank())
            sb.append("Phone: ").append(safe(u.getPhone())).append("\n");
        sb.append("Role: ").append(roles).append("\n");
        sb.append("Email verified: ").append(u.isEmailVerified() ? "✅ yes" : "❌ no").append("\n");
        if (u.getCreatedAt() != null)
            sb.append("Member since: ").append(formatDate(u.getCreatedAt())).append("\n");
        // Wallet balance on the same card — best-effort, never let it break the profile view.
        try {
            JsonNode w = api.wallet(u);
            sb.append("Balance: *$").append(w.path("availableBalance").asText("0")).append("*\n");
        } catch (Exception ignored) { }
        send(chatId, sb.toString(), rows(
                List.of(btn("💰 Wallet", "act:wallet"), btn("📧 My Emails", "act:emails")),
                List.of(urlBtn("✏️ Edit on website", siteUrl() + "/profile")),
                List.of(btn("🏠 Menu", "act:menu"))));
    }

    private static String blankDash(String s) { return (s == null || s.isBlank()) ? "—" : s; }

    private static String formatDate(java.time.Instant i) {
        return java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy")
                .withZone(java.time.ZoneOffset.UTC).format(i);
    }

    private void showWallet(Long chatId) {
        User u = requireUser(chatId); if (u == null) return;
        JsonNode w = api.wallet(u);
        boolean seller = isSeller(u), buyer = isBuyer(u);
        String txt = "*Your wallet*\nAvailable: *$" + w.path("availableBalance").asText("0") + "*\n"
                + "Pending: $" + w.path("pendingBalance").asText("0") + "\n"
                + "Total earnings: $" + w.path("totalEarnings").asText("0");
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        // Buyers deposit & track orders; sellers withdraw earnings (on the website). Orders are a buyer
        // concept, so a seller-only account never sees them here.
        if (buyer) rows.add(List.of(btn("➕ Deposit", "act:deposit"), btn("🧾 Orders", "act:orders")));
        if (seller) rows.add(List.of(urlBtn("💸 Withdraw (website)", siteUrl() + "/seller/wallet")));
        rows.add(List.of(btn("🏠 Menu", "act:menu")));
        send(chatId, txt, rows);
    }

    private void startDeposit(Long chatId, Session s) {
        if (requireUser(chatId) == null) return;
        String payId = payId();
        String caption = "➕ *Deposit via Binance Pay*\n\n"
                + "• Scan this QR in Binance Pay, or\n"
                + (payId != null ? "• Send to Binance *UID*: `" + payId + "`\n" : "")
                + "\nAfter paying, enter the *amount* and your *transaction ID* here to get credited.";
        byte[] qr = resolveQr();
        if (qr != null) sendPhoto(chatId, qr, caption);
        else send(chatId, caption);
        s.step = Step.DEPOSIT_AMOUNT;
        send(chatId, "How much are you depositing (USD)? e.g. `25`", cancelKb());
    }

    /** Configured Binance Pay UID buyers deposit to (from admin settings), or null if unset. */
    private String payId() {
        return settings.findById(PAY_ID_KEY).map(Setting::getValue).filter(v -> !v.isBlank()).orElse(null);
    }

    /** Current deposit QR bytes — the admin-uploaded image if present, else the bundled default. */
    private byte[] resolveQr() {
        try {
            var uploaded = media.find(QR_NAME);
            if (uploaded.isPresent()) return uploaded.get().getData();
            ClassPathResource res = new ClassPathResource(BUNDLED_QR);
            if (res.exists()) { try (InputStream in = res.getInputStream()) { return in.readAllBytes(); } }
        } catch (Exception e) {
            log.warn("[TELEGRAM] could not load deposit QR: {}", e.toString());
        }
        return null;
    }

    private void startSell(Long chatId, Session s) {
        User u = requireUser(chatId); if (u == null) return;
        if (!isSeller(u)) {
            send(chatId, "🏷 Selling needs a *seller* account. Register as a seller on the website first.",
                    rows(List.of(urlBtn("🌐 Become a seller", siteUrl() + "/register")), List.of(btn("🏠 Menu", "act:menu"))));
            return;
        }
        resetConv(s);
        // Step 1 (mirrors the website): choose the account category. The price and warranty are FIXED
        // per category by the admin — the seller is never asked for a price.
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        // Only offer categories we're actually buying (a positive payout price). Bot lists Gmail.
        for (AccountCategory c : AccountCategory.values())
            if (pricing.isBuyable(store.mailstock.submission.entity.SellerSubmission.Provider.GMAIL, c))
                rows.add(List.of(btn(c.label, "scat:" + c.name())));
        rows.add(List.of(btn("🏠 Menu", "act:menu")));
        if (rows.size() == 1) {
            send(chatId, "🏷 *List an account*\n\nWe're not buying any accounts right now — no categories are priced. "
                    + "Please check back soon.", rows(List.of(btn("🏠 Menu", "act:menu"))));
            return;
        }
        send(chatId, "🏷 *List an account*\n\nFirst pick the *category* of the account you're selling. "
                + "The payout is set automatically by category (same as the website).", rows);
    }

    /** Step 2 of selling: a category was chosen — collect the account's email/password/country next. */
    private void startSellDetails(Long chatId, Session s, String catKey) {
        User u = requireUser(chatId); if (u == null) return;
        if (!isSeller(u)) { startSell(chatId, s); return; }
        AccountCategory cat;
        try { cat = AccountCategory.valueOf(catKey); }
        catch (RuntimeException e) { send(chatId, "That category is no longer valid. Tap 🏷 Sell to start again.", menuOnly()); return; }
        s.data.clear();
        s.data.put("accountCategory", cat.name());
        s.step = Step.SELL_EMAIL;
        send(chatId, "🏷 *" + safe(cat.label) + "*\n\nSend the account's *email address*:", cancelKb());
    }

    /** Chosen sell category from the session, or null if it's missing/invalid. */
    private static AccountCategory categoryOf(Session s) {
        Object v = s.data.get("accountCategory");
        if (v == null) return null;
        try { return AccountCategory.valueOf(v.toString()); } catch (RuntimeException e) { return null; }
    }

    /** Final sell step: build the submission (price fixed by category — none is sent) and post it. */
    private void submitSell(Long chatId, Session s) {
        User u = requireUser(chatId); if (u == null) { resetConv(s); return; }
        AccountCategory cat = categoryOf(s);
        if (cat == null) { resetConv(s); send(chatId, "That sell session expired. Tap 🏷 Sell to start again.", menuOnly()); return; }
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("emailAddress", s.data.get("emailAddress"));
        body.put("emailPassword", s.data.get("emailPassword"));
        body.put("country", s.data.get("country"));
        body.put("provider", "GMAIL");                 // bot lists Gmail; provider×category still fixes the price
        body.put("accountCategory", cat.name());
        if (s.data.get("twoFactorCode") != null) body.put("twoFactorCode", s.data.get("twoFactorCode"));
        resetConv(s);
        JsonNode d = api.submit(u, body);
        send(chatId, "✅ Submission #" + d.path("id").asText("?") + " sent for review.\n"
                + "_The payout for this category is set automatically — same as the website._",
                rows(List.of(btn("🏷 Sell another", "act:sell")), List.of(btn("🏠 Menu", "act:menu"))));
    }

    /** Seller's submissions list with status, so they can track review progress from the bot. */
    private void showSubmissions(Long chatId) {
        User u = requireUser(chatId); if (u == null) return;
        if (!isSeller(u)) {
            send(chatId, "📋 Submissions are for *seller* accounts. Register as a seller on the website to start selling.",
                    rows(List.of(urlBtn("🌐 Become a seller", siteUrl() + "/register")), List.of(btn("🏠 Menu", "act:menu"))));
            return;
        }
        JsonNode content = api.mySubmissions(u).path("content");
        if (!content.isArray() || content.isEmpty()) {
            send(chatId, "📋 *Your submissions*\n\nYou haven't submitted any accounts yet. Tap *📤 New submission* to list one.",
                    rows(List.of(btn("📤 New submission", "act:sell")), List.of(btn("🏠 Menu", "act:menu"))));
            return;
        }
        StringBuilder sb = new StringBuilder("*📋 Your submissions*\n\n");
        for (JsonNode sub : content) {
            String email = sub.path("emailAddress").asText("");
            sb.append("• #").append(sub.path("id").asText("?"))
              .append(" — ").append(sub.path("provider").asText(""))
              .append(email.isBlank() ? "" : " · " + safe(maskEmail(email)))
              .append(" — *$").append(sub.path("askingPrice").asText("?")).append("*")
              .append(" · ").append(statusLabel(sub.path("status").asText("PENDING"))).append("\n");
        }
        sb.append("\n_Manage full details, 2FA and pricing on the website._");
        send(chatId, sb.toString(), rows(
                List.of(btn("📤 New submission", "act:sell")),
                List.of(urlBtn("🌐 Open on website", siteUrl() + "/seller/submissions"), btn("🏠 Menu", "act:menu"))));
    }

    /** Friendly status wording for a submission's raw enum. */
    private static String statusLabel(String status) {
        return switch (status) {
            case "PENDING" -> "🕓 Pending review";
            case "CHECKING" -> "🔎 Under review";
            case "APPROVED", "ACCEPTED" -> "✅ Approved";
            case "PURCHASED" -> "💵 Paid";
            case "REJECTED" -> "❌ Rejected";
            case "COUNTER_OFFERED" -> "💬 Counter offer";
            case "NEEDS_MODIFY" -> "✏️ Needs changes";
            default -> status;
        };
    }

    /** Mask an email for display: first 3 + *** + last 2 of the local part. */
    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at < 0) return email;
        String local = email.substring(0, at), domain = email.substring(at);
        String masked = local.length() <= 5 ? local.charAt(0) + "***"
                : local.substring(0, 3) + "***" + local.substring(local.length() - 2);
        return masked + domain;
    }

    private void showOrders(Long chatId) {
        User u = requireUser(chatId); if (u == null) return;
        JsonNode content = api.myOrders(u).path("content");
        if (!content.isArray() || content.isEmpty()) {
            send(chatId, "No orders yet.", rows(List.of(btn("🛍 Browse", "act:browse")), List.of(btn("🏠 Menu", "act:menu")))); return;
        }
        StringBuilder sb = new StringBuilder("*🧾 Your recent orders*\n\n");
        for (JsonNode o : content)
            sb.append("• Order #").append(o.path("id").asText()).append(" — *$").append(o.path("totalAmount").asText("?"))
              .append("* · ").append(o.path("status").asText("")).append("\n");
        sb.append("\n_Tap My Emails to view the login details for every account you've bought._");
        send(chatId, sb.toString(), rows(List.of(btn("📧 My Emails", "act:emails")), List.of(btn("🏠 Menu", "act:menu"))));
    }

    /** The buyer's vault: every account they've purchased, each with its full login credentials. */
    private void showMyEmails(Long chatId) {
        User u = requireUser(chatId); if (u == null) return;
        JsonNode arr = api.myEmails(u);
        if (!arr.isArray() || arr.isEmpty()) {
            send(chatId, "📭 You haven't purchased any accounts yet.",
                    rows(List.of(btn("🛍 Browse & Buy", "act:browse")), List.of(btn("🏠 Menu", "act:menu"))));
            return;
        }
        int total = arr.size();
        send(chatId, "*📧 Your accounts* (" + total + ")\nHere are the login details for everything you've bought:");
        int shown = 0;
        for (JsonNode e : arr) {
            if (shown >= 20) { send(chatId, "_Showing your 20 most recent. Open the website to see the rest._"); break; }
            String creds = e.path("deliveryPayload").asText("");
            send(chatId, "🔑 *Order #" + e.path("orderId").asText("?") + "*\n```\n"
                    + (creds.isBlank() ? "Credentials unavailable — please contact support." : creds) + "\n```");
            shown++;
        }
        send(chatId, "_Keep these safe._",
                rows(List.of(btn("🛍 Browse & Buy", "act:browse")), List.of(btn("🏠 Menu", "act:menu"))));
    }

    /** How-it-works guide: what MailStock is, how to get started on the website, and every bot action. */
    private void showHelp(Long chatId) {
        boolean linked = links.resolveUser(chatId).isPresent();
        String site = siteUrl();
        String txt = "*❓ How MailStock works*\n\n"
                + "MailStock is a marketplace for ready-made email accounts (Gmail, Outlook & more). "
                + "Buyers get login details instantly; sellers submit accounts for review and get paid.\n\n"
                + "*1️⃣ Create your account*\n"
                + "Sign up on the website — " + site + "/register — then verify your email. "
                + "Choose *Buyer* to purchase or *Seller* to sell.\n\n"
                + "*2️⃣ Connect this bot*\n"
                + (linked ? "✅ You're connected.\n"
                          : "On the website open *Profile → Connect Telegram*, copy the code, tap *🔗 Connect with code* below and send it here.\n")
                + "\n*3️⃣ Add funds*\n"
                + "Tap *➕ Deposit* → pay via Binance Pay → send the amount & transaction ID. Your balance is credited automatically.\n\n"
                + "*4️⃣ Buy an account*\n"
                + "Tap *🛍 Browse & Buy* → pick a category → confirm. The login details appear instantly and stay saved under *📧 My Emails*.\n\n"
                + "*5️⃣ Sell accounts* (sellers)\n"
                + "Tap *🏷 Sell* to list an account for review. Manage age/2FA details and pricing on the website.\n\n"
                + "*🛡 Warranty*\n"
                + "If an account you bought stops working within its warranty period, tap *🛡 Warranty* to file a claim — one claim per account. An admin will replace it or refund you.\n\n"
                + "*🛟 Support*\n"
                + "Tap *🛟 Support* to open a ticket. We reply right here in Telegram and on the website.\n\n"
                + "_Full site: " + site + "_";
        send(chatId, txt, rows(
                List.of(btn("🛟 Open a ticket", "act:support"), btn("🛡 Claim warranty", "act:warranty")),
                List.of(btn("🛍 Browse & Buy", "act:browse")),
                linked ? List.of(btn("🏠 Menu", "act:menu"))
                       : List.of(urlBtn("🌐 Create account", site + "/register"), btn("🔗 Connect with code", "act:linkcode"))));
    }

    /** Support: open a ticket. Anyone connected can do this; sellers/buyers alike. */
    private void startTicket(Long chatId, Session s) {
        if (requireUser(chatId) == null) return;
        s.step = Step.TICKET_SUBJECT; s.data.clear();
        send(chatId, "🛟 *Open a support ticket*\n\nWhat's it about? Send a short *subject* (e.g. \"Deposit not credited\"):",
                cancelKb());
    }

    /**
     * Warranty step 1: list the buyer's purchased accounts so they can pick which one to claim on.
     * The API enforces eligibility (delivered, in-warranty, not already claimed) when the claim is filed.
     */
    private void startWarranty(Long chatId) {
        User u = requireUser(chatId); if (u == null) return;
        JsonNode arr = api.myEmails(u);
        if (!arr.isArray() || arr.isEmpty()) {
            send(chatId, "🛡 *Warranty*\n\nYou haven't purchased any accounts yet, so there's nothing to claim.",
                    rows(List.of(btn("🛍 Browse & Buy", "act:browse")), List.of(btn("🏠 Menu", "act:menu"))));
            return;
        }
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        StringBuilder sb = new StringBuilder("🛡 *File a warranty claim*\n\nPick the account that stopped working "
                + "(only one claim is allowed per account):\n\n");
        int shown = 0;
        for (JsonNode e : arr) {
            if (shown >= 15) break;
            long oid = e.path("id").asLong();
            if (oid == 0) continue;
            String title = e.path("title").asText("Account");
            String order = e.path("orderId").asText("?");
            sb.append("• Order #").append(order).append(" — ").append(safe(title)).append("\n");
            rows.add(List.of(btn("🛡 Claim · #" + order + " " + shortText(title, 18), "wsel:" + oid)));
            shown++;
        }
        rows.add(List.of(btn("🏠 Menu", "act:menu")));
        send(chatId, sb.toString(), rows);
    }

    /** Warranty step 2: buyer picked an account — ask them to describe the problem, then submit. */
    private void pickWarrantyItem(Long chatId, Session s, long orderItemId) {
        if (requireUser(chatId) == null) return;
        if (orderItemId <= 0) { send(chatId, "That account button is no longer valid.", menuOnly()); return; }
        s.step = Step.WARRANTY_DESC; s.data.clear();
        s.data.put("orderItemId", orderItemId);
        send(chatId, "🛡 *Describe the problem*\n\nWhat's wrong with this account? "
                + "(e.g. \"can't log in\", \"account disabled\", \"2FA not working\")", cancelKb());
    }

    // ---------- cart & multi-buy ----------

    private void addToCart(Long chatId, Session s, long id) {
        User u = requireUser(chatId); if (u == null) return;
        if (id <= 0) { send(chatId, "That item is no longer valid.", menuOnly()); return; }
        if (s.cart.contains(id)) { send(chatId, "Already in your cart.", cartNav(s)); return; }
        if (s.cart.size() >= CART_MAX) {
            send(chatId, "🛒 Your cart is full (max " + CART_MAX + " accounts). Check out first.", cartNav(s)); return;
        }
        JsonNode it = api.item(id); // validates the item is still browsable / available
        s.cart.add(id);
        s.cartLabels.put(id, it.path("provider").asText("Account") + " · $" + it.path("sellingPrice").asText("?"));
        send(chatId, "✅ Added to cart. You now have *" + s.cart.size() + "* item(s).", cartNav(s));
    }

    private void removeFromCart(Long chatId, Session s, long id) {
        s.cart.remove(id); s.cartLabels.remove(id);
        showCart(chatId, s);
    }

    private void clearCart(Long chatId, Session s) {
        s.cart.clear(); s.cartLabels.clear(); s.coupon = null;
        send(chatId, "🗑 Cart cleared.",
                rows(List.of(btn("🛍 Browse & Buy", "act:browse")), List.of(btn("🏠 Menu", "act:menu"))));
    }

    private void startCoupon(Long chatId, Session s) {
        if (requireUser(chatId) == null) return;
        if (s.cart.isEmpty()) { showCart(chatId, s); return; }
        s.step = Step.CART_COUPON;
        send(chatId, "🏷 Send your *coupon code* (or tap Cancel to skip):", cancelKb());
    }

    /** Cart summary with a live server quote (subtotal, discount, total) + per-item remove buttons. */
    private void showCart(Long chatId, Session s) {
        User u = requireUser(chatId); if (u == null) return;
        if (s.cart.isEmpty()) {
            send(chatId, "🛒 *Your cart is empty.*\nAdd accounts from Browse, then buy up to " + CART_MAX + " at once.",
                    rows(List.of(btn("🛍 Browse & Buy", "act:browse")), List.of(btn("🏠 Menu", "act:menu"))));
            return;
        }
        JsonNode q = api.quote(u, new ArrayList<>(s.cart), s.coupon);
        StringBuilder sb = new StringBuilder("*🛒 Your cart* (" + s.cart.size() + "/" + CART_MAX + ")\n\n");
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (Long id : s.cart) {
            String label = s.cartLabels.getOrDefault(id, "Account #" + id);
            sb.append("• ").append(safe(label)).append("\n");
            rows.add(List.of(btn("🗑 Remove " + shortText(label, 16), "crm:" + id)));
        }
        sb.append("\nSubtotal: *$").append(q.path("subtotal").asText("?")).append("*");
        if (q.path("discount").asDouble(0) > 0)
            sb.append("\nDiscount: -$").append(q.path("discount").asText("0"));
        sb.append("\n*Total: $").append(q.path("payable").asText("?")).append("*");
        if (s.coupon != null && !s.coupon.isBlank()) {
            if (q.path("couponApplied").asBoolean(false))
                sb.append("\n✅ Coupon `").append(safe(s.coupon)).append("` applied");
            else
                sb.append("\n⚠️ Coupon `").append(safe(s.coupon)).append("`: ")
                  .append(safe(q.path("couponMessage").asText("not applied")));
        }
        rows.add(List.of(btn("✅ Checkout · $" + q.path("payable").asText("?"), "act:checkout")));
        rows.add(List.of(btn(s.coupon == null ? "🏷 Add coupon" : "🏷 Change coupon", "act:coupon"),
                btn("🗑 Clear", "act:cartclear")));
        rows.add(List.of(btn("🛍 Keep browsing", "act:browse"), btn("🏠 Menu", "act:menu")));
        send(chatId, sb.toString(), rows);
    }

    /** Charge the whole cart in one order, deliver every account, then empty the cart. */
    private void checkout(Long chatId, Session s) {
        User u = requireUser(chatId); if (u == null) return;
        if (s.cart.isEmpty()) { showCart(chatId, s); return; }
        // Throws (insufficient funds / item gone / bad coupon) → caught upstream; cart is preserved so the user can retry.
        JsonNode order = api.buy(u, new ArrayList<>(s.cart), s.coupon);
        s.cart.clear(); s.cartLabels.clear(); s.coupon = null;
        sendOrderDelivery(chatId, order);
    }

    private static List<List<InlineKeyboardButton>> cartNav(Session s) {
        return rows(List.of(btn("🛒 View cart (" + s.cart.size() + ")", "act:cart")),
                List.of(btn("🛍 Keep browsing", "act:browse"), btn("🏠 Menu", "act:menu")));
    }

    /** Public site URL from the {@code site.url} setting, falling back to the default domain. */
    private String siteUrl() {
        return settings.findById("site.url").map(Setting::getValue).filter(v -> !v.isBlank())
                .map(v -> v.endsWith("/") ? v.substring(0, v.length() - 1) : v)
                .orElse(DEFAULT_SITE_URL);
    }

    private void sendMenu(Long chatId) {
        Optional<User> linkedUser = links.resolveUser(chatId);
        boolean linked = linkedUser.isPresent();
        String site = siteUrl();

        if (!linked) {
            promptConnect(chatId);
            return;
        }

        // Menu is tailored to the account's role: sellers get selling tools, buyers get shopping tools.
        // A user with both roles sees the seller menu plus a Browse entry so they can still buy.
        User u = linkedUser.get();
        if (isSeller(u)) sendSellerMenu(chatId, u, site);
        else sendBuyerMenu(chatId, site);
    }

    /** Seller home: submissions, new listing, wallet/payouts, support, profile & settings (on the website). */
    private void sendSellerMenu(Long chatId, User u, String site) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(btn("📋 My Submissions", "act:submissions"), btn("📤 New submission", "act:sell")));
        rows.add(List.of(btn("💰 Wallet & payouts", "act:wallet")));
        rows.add(List.of(btn("🛟 Support", "act:support"), btn("❓ Help", "act:help")));
        if (isBuyer(u)) rows.add(List.of(btn("🛍 Browse & Buy", "act:browse")));
        rows.add(List.of(btn("👤 Profile", "act:profile"), urlBtn("⚙️ Settings", site + "/settings")));
        rows.add(List.of(btn("🚪 Logout", "act:logout")));
        send(chatId, "*🏷 Seller menu*\nManage your submissions and payouts. What would you like to do?", rows);
    }

    /** Buyer home: browse/cart/checkout, wallet & deposit, orders, purchased emails, warranty, support. */
    private void sendBuyerMenu(Long chatId, String site) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(btn("🛍 Browse & Buy", "act:browse"), btn("🛒 Cart", "act:cart")));
        rows.add(List.of(btn("💰 Wallet", "act:wallet"), btn("➕ Deposit", "act:deposit")));
        rows.add(List.of(btn("🧾 Orders", "act:orders"), btn("📧 My Emails", "act:emails")));
        rows.add(List.of(btn("🛡 Warranty", "act:warranty"), btn("🛟 Support", "act:support")));
        rows.add(List.of(btn("❓ Help / How it works", "act:help")));
        rows.add(List.of(btn("👤 Profile", "act:profile"), urlBtn("⚙️ Settings", site + "/settings")));
        rows.add(List.of(btn("🚪 Logout", "act:logout")));
        send(chatId, "*🛒 MailStock.store*\nYour account marketplace. What would you like to do?", rows);
    }

    private static boolean isSeller(User u) {
        return u.getRoles().stream().anyMatch(r -> r.name().equals("SELLER"));
    }
    private static boolean isBuyer(User u) {
        return u.getRoles().stream().anyMatch(r -> r.name().equals("BUYER"));
    }

    // ---------- helpers ----------
    private User requireUser(Long chatId) {
        Optional<User> u = links.resolveUser(chatId);
        if (u.isEmpty()) { promptConnect(chatId); return null; }
        return u.get();
    }

    private void resetConv(Session s) { s.step = Step.NONE; s.data.clear(); }

    /**
     * Pull the bare code out of whatever the user typed. Tolerates them pasting the full command
     * form ("/link ABCD1234" or "/start ABCD1234") while the LINK_CODE step is armed — without this,
     * the entire "/link ABCD1234" string would be treated as the code and rejected as invalid.
     */
    private static String extractLinkCode(String text) {
        String t = text == null ? "" : text.trim();
        if (t.startsWith("/")) {                       // strip a leading /link or /start command word
            String[] parts = t.split("\\s+", 2);
            t = parts.length > 1 ? parts[1].trim() : "";
        }
        return t;
    }

    private static BigDecimal parseAmount(String s) {
        try { BigDecimal b = new BigDecimal(s.replace("$", "").replace(",", "").trim()); return b.signum() > 0 ? b : null; }
        catch (Exception e) { return null; }
    }
    private static int parseInt(String s) { try { return Integer.parseInt(s); } catch (Exception e) { return 0; } }
    private static long parseLong(String s) { try { return Long.parseLong(s); } catch (Exception e) { return 0; } }

    private static InlineKeyboardButton btn(String text, String data) {
        return InlineKeyboardButton.builder().text(text).callbackData(data).build();
    }

    /** A button that opens a URL (e.g. the website) instead of firing a callback. */
    private static InlineKeyboardButton urlBtn(String text, String url) {
        return InlineKeyboardButton.builder().text(text).url(url).build();
    }

    @SafeVarargs
    private static List<List<InlineKeyboardButton>> rows(List<InlineKeyboardButton>... r) {
        return new ArrayList<>(List.of(r));
    }
    private static List<List<InlineKeyboardButton>> menuOnly() { return rows(List.of(btn("🏠 Menu", "act:menu"))); }
    private static List<List<InlineKeyboardButton>> cancelKb() { return rows(List.of(btn("✖️ Cancel", "act:cancel"))); }

    /** Escape Telegram Markdown (v1) special chars so titles/emails don't break formatting. */
    private static String safe(String s) {
        if (s == null) return "";
        return s.replace("_", "\\_").replace("*", "\\*").replace("`", "\\`").replace("[", "\\[");
    }

    /** Trim a label to fit inside a button (no Markdown — button text is plain). */
    private static String shortText(String s, int max) {
        if (s == null) return "";
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, Math.max(0, max - 1)) + "…";
    }

    private static Long chatIdOf(Update u) {
        if (u.hasMessage()) return u.getMessage().getChatId();
        if (u.hasCallbackQuery()) return u.getCallbackQuery().getMessage().getChatId();
        return null;
    }

    private void send(Long chatId, String text) { send(chatId, text, null); }

    private void send(Long chatId, String text, List<List<InlineKeyboardButton>> rows) {
        SendMessage.SendMessageBuilder b = SendMessage.builder()
                .chatId(chatId.toString()).text(text).parseMode("Markdown");
        if (rows != null && !rows.isEmpty())
            b.replyMarkup(InlineKeyboardMarkup.builder().keyboard(rows).build());
        try { execute(b.build()); }
        catch (TelegramApiException e) { log.warn("[TELEGRAM] send failed: {}", e.toString()); }
    }

    private void sendPhoto(Long chatId, byte[] image, String caption) {
        try {
            execute(SendPhoto.builder()
                    .chatId(chatId.toString())
                    .photo(new InputFile(new ByteArrayInputStream(image), "deposit-qr.jpg"))
                    .caption(caption).parseMode("Markdown").build());
        } catch (TelegramApiException e) {
            log.warn("[TELEGRAM] sendPhoto failed: {}", e.toString());
            send(chatId, caption); // fall back to text so the UID is still shown
        }
    }

    private void deleteMessage(Long chatId, Integer messageId) {
        if (messageId == null) return;
        try { execute(DeleteMessage.builder().chatId(chatId.toString()).messageId(messageId).build()); }
        catch (TelegramApiException ignored) { }
    }

    private void ack(String callbackId) {
        try { execute(AnswerCallbackQuery.builder().callbackQueryId(callbackId).build()); }
        catch (TelegramApiException ignored) { }
    }
}
