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
    private static final String QR_FILE = "deposit-qr";
    private static final String BUNDLED_QR = "images/binance qr.jpg";

    private final String username;
    private final TelegramLinkService links;
    private final BotApiClient api;
    private final SettingRepository settings;
    private final String uploadsDir;

    private enum Step { NONE, LINK_CODE, DEPOSIT_AMOUNT, DEPOSIT_TXID,
        SELL_EMAIL, SELL_PASSWORD, SELL_COUNTRY, SELL_PRICE }

    private static final class Session {
        Step step = Step.NONE;
        final Map<String, Object> data = new ConcurrentHashMap<>();
    }
    private final ConcurrentHashMap<Long, Session> sessions = new ConcurrentHashMap<>();

    public MailStockBot(String botToken, String username, TelegramLinkService links, BotApiClient api,
                        SettingRepository settings, String uploadsDir) {
        super(botToken);
        this.username = username;
        this.links = links;
        this.api = api;
        this.settings = settings;
        this.uploadsDir = uploadsDir;
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
        switch (cmd) {
            case "/start" -> { if (!arg.isBlank()) doLinkCode(chatId, arg); else sendMenu(chatId); }
            case "/menu", "/help" -> sendMenu(chatId);
            case "/link" -> { if (arg.isBlank()) startLinkCode(chatId, s); else doLinkCode(chatId, arg); }
            case "/logout" -> doLogout(chatId, s);
            case "/browse" -> showBrowse(chatId, 0);
            case "/wallet" -> showWallet(chatId);
            case "/deposit" -> startDeposit(chatId, s);
            case "/sell" -> startSell(chatId, s);
            case "/orders" -> showOrders(chatId);
            case "/myemails" -> showMyEmails(chatId);
            case "/cancel" -> { resetConv(s); send(chatId, "Cancelled.", menuOnly()); }
            default -> send(chatId,
                    "🤔 I didn't understand *" + safe(text) + "*.\nUse the menu below or type /menu.",
                    menuOnly());
        }
    }

    private void handleConversation(Long chatId, Session s, String text, Integer messageId) {
        switch (s.step) {
            case LINK_CODE -> { doLinkCode(chatId, text); resetConv(s); }
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
                s.data.put("country", text); s.step = Step.SELL_PRICE;
                send(chatId, "Your *asking price* in USD, e.g. `4.50`:", cancelKb());
            }
            case SELL_PRICE -> {
                BigDecimal price = parseAmount(text);
                if (price == null) { send(chatId, "Enter a valid price, e.g. `4.50`.", cancelKb()); return; }
                User u = requireUser(chatId); if (u == null) { resetConv(s); return; }
                Map<String, Object> body = new java.util.HashMap<>(s.data);
                body.put("askingPrice", price); body.put("provider", "GMAIL"); body.put("accountType", "NEW");
                resetConv(s);
                JsonNode d = api.submit(u, body);
                send(chatId, "✅ Submission #" + d.path("id").asText("?") + " sent for review.",
                        rows(List.of(btn("🏷 Sell another", "act:sell")), List.of(btn("🏠 Menu", "act:menu"))));
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

        // Any navigation/menu tap cancels an in-progress flow (context switch).
        if (data.startsWith("act:")) {
            resetConv(s);
            switch (data.substring(4)) {
                case "menu", "home" -> sendMenu(chatId);
                case "browse" -> showBrowse(chatId, 0);
                case "wallet" -> showWallet(chatId);
                case "deposit" -> startDeposit(chatId, s);
                case "orders" -> showOrders(chatId);
                case "emails" -> showMyEmails(chatId);
                case "sell" -> startSell(chatId, s);
                case "linkcode" -> startLinkCode(chatId, s);
                case "logout" -> doLogout(chatId, s);
                case "cancel" -> send(chatId, "Cancelled.", menuOnly());
                default -> sendMenu(chatId);
            }
            return;
        }
        if (data.startsWith("browse:")) showBrowse(chatId, parseInt(data.substring(7)));
        else if (data.startsWith("item:")) showItem(chatId, parseLong(data.substring(5)));
        else if (data.startsWith("buy:")) doBuy(chatId, parseLong(data.substring(4)));
        else send(chatId, "That button is no longer valid.", menuOnly());
    }

    // ---------- flows ----------
    private void startLinkCode(Long chatId, Session s) {
        s.step = Step.LINK_CODE; s.data.clear();
        send(chatId, "🔗 *Connect with a code*\nGet a code on the website (Profile → Connect Telegram) and send it here:",
                cancelKb());
    }

    private void doLinkCode(Long chatId, String code) {
        User u = links.consumeCode(code, chatId); // throws ApiException with a clear reason on failure
        send(chatId, "✅ Connected as *" + safe(u.getEmail()) + "*.");
        sendMenu(chatId);
    }

    private void doLogout(Long chatId, Session s) {
        boolean was = links.resolveUser(chatId).isPresent();
        links.unlink(chatId); resetConv(s);
        send(chatId, was ? "🚪 Disconnected. Use Login to reconnect." : "You weren't connected.", menuOnly());
    }

    private void showBrowse(Long chatId, int page) {
        JsonNode d = api.browse(page);
        JsonNode content = d.path("content");
        if (!content.isArray() || content.isEmpty()) {
            send(chatId, "No items are available right now. Check back soon.", menuOnly()); return;
        }
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        StringBuilder sb = new StringBuilder("*Available accounts* (page " + (page + 1) + ")\n\n");
        for (JsonNode it : content) {
            long id = it.path("id").asLong();
            sb.append("• #").append(id).append(" ").append(safe(it.path("title").asText()))
              .append(" — *$").append(it.path("sellingPrice").asText("?")).append("* (")
              .append(it.path("provider").asText("")).append(", ").append(it.path("country").asText("")).append(")\n");
            rows.add(List.of(btn("🛒 Buy #" + id + " · $" + it.path("sellingPrice").asText("?"), "buy:" + id),
                    btn("🔍 Details", "item:" + id)));
        }
        int totalPages = d.path("totalPages").asInt(1);
        List<InlineKeyboardButton> nav = new ArrayList<>();
        if (page > 0) nav.add(btn("⬅️ Prev", "browse:" + (page - 1)));
        if (page + 1 < totalPages) nav.add(btn("Next ➡️", "browse:" + (page + 1)));
        if (!nav.isEmpty()) rows.add(nav);
        rows.add(List.of(btn("🏠 Menu", "act:menu")));
        send(chatId, sb.toString(), rows);
    }

    private void showItem(Long chatId, long id) {
        JsonNode it = api.item(id);
        String txt = "*#" + id + " " + safe(it.path("title").asText()) + "*\n"
                + "Price: *$" + it.path("sellingPrice").asText("?") + "*\n"
                + "Provider: " + it.path("provider").asText("") + " · Type: " + it.path("accountType").asText("") + "\n"
                + "Country: " + it.path("country").asText("") + " · Warranty: " + it.path("warrantyDays").asText("0") + "d\n\n"
                + safe(it.path("description").asText(""));
        send(chatId, txt, rows(
                List.of(btn("🛒 Buy now", "buy:" + id)),
                List.of(btn("⬅️ Back to list", "act:browse"), btn("🏠 Menu", "act:menu"))));
    }

    private void doBuy(Long chatId, long id) {
        User u = requireUser(chatId); if (u == null) return;
        JsonNode order = api.buy(u, List.of(id), null);
        send(chatId, "✅ Purchased! Order #" + order.path("id").asText("?")
                        + " — total *$" + order.path("totalAmount").asText("?") + "*, " + order.path("status").asText(""),
                rows(List.of(btn("📧 My Emails", "act:emails")),
                        List.of(btn("🛍 Browse more", "act:browse"), btn("🏠 Menu", "act:menu"))));
    }

    private void showWallet(Long chatId) {
        User u = requireUser(chatId); if (u == null) return;
        JsonNode w = api.wallet(u);
        String txt = "*Your wallet*\nAvailable: *$" + w.path("availableBalance").asText("0") + "*\n"
                + "Pending: $" + w.path("pendingBalance").asText("0") + "\n"
                + "Total earnings: $" + w.path("totalEarnings").asText("0");
        send(chatId, txt, rows(
                List.of(btn("➕ Deposit", "act:deposit"), btn("📦 Orders", "act:orders")),
                List.of(btn("🏠 Menu", "act:menu"))));
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
            Path uploaded = Path.of(uploadsDir, QR_FILE);
            if (Files.exists(uploaded)) return Files.readAllBytes(uploaded);
            ClassPathResource res = new ClassPathResource(BUNDLED_QR);
            if (res.exists()) { try (InputStream in = res.getInputStream()) { return in.readAllBytes(); } }
        } catch (Exception e) {
            log.warn("[TELEGRAM] could not load deposit QR: {}", e.toString());
        }
        return null;
    }

    private void startSell(Long chatId, Session s) {
        User u = requireUser(chatId); if (u == null) return;
        if (u.getRoles().stream().noneMatch(r -> r.name().equals("SELLER"))) {
            send(chatId, "🏷 Selling needs a *seller* account. Register as a seller on the website first.", menuOnly());
            return;
        }
        s.step = Step.SELL_EMAIL; s.data.clear();
        send(chatId, "🏷 *List an account*\nSend the *email address*:", cancelKb());
    }

    private void showOrders(Long chatId) {
        User u = requireUser(chatId); if (u == null) return;
        JsonNode content = api.myOrders(u).path("content");
        if (!content.isArray() || content.isEmpty()) {
            send(chatId, "No orders yet.", rows(List.of(btn("🛍 Browse", "act:browse")), List.of(btn("🏠 Menu", "act:menu")))); return;
        }
        StringBuilder sb = new StringBuilder("*Your recent orders*\n\n");
        for (JsonNode o : content)
            sb.append("• #").append(o.path("id").asText()).append(" — $").append(o.path("totalAmount").asText("?"))
              .append(" · ").append(o.path("status").asText("")).append("\n");
        send(chatId, sb.toString(), rows(List.of(btn("📧 My Emails", "act:emails")), List.of(btn("🏠 Menu", "act:menu"))));
    }

    private void showMyEmails(Long chatId) {
        User u = requireUser(chatId); if (u == null) return;
        JsonNode arr = api.myEmails(u);
        if (!arr.isArray() || arr.isEmpty()) {
            send(chatId, "No delivered emails yet.", menuOnly()); return;
        }
        StringBuilder sb = new StringBuilder("*Your purchased emails*\n\n");
        for (JsonNode e : arr)
            sb.append("• ").append(safe(e.path("deliveredEmail").asText(e.path("title").asText("(item)")))).append("\n");
        send(chatId, sb.toString(), menuOnly());
    }

    private void sendMenu(Long chatId) {
        boolean linked = links.resolveUser(chatId).isPresent();
        String txt = "*MailStock.store*\n" + (linked ? "Choose an option:"
                : "_Not connected yet — connect with a code from the website (Profile → Connect Telegram)._");
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(btn("🛍 Browse & Buy", "act:browse"), btn("💰 Wallet", "act:wallet")));
        rows.add(List.of(btn("➕ Deposit", "act:deposit"), btn("📦 Orders", "act:orders")));
        rows.add(List.of(btn("📧 My Emails", "act:emails"), btn("🏷 Sell", "act:sell")));
        rows.add(linked
                ? List.of(btn("🚪 Logout", "act:logout"))
                : List.of(btn("🔗 Connect with code", "act:linkcode")));
        send(chatId, txt, rows);
    }

    // ---------- helpers ----------
    private User requireUser(Long chatId) {
        Optional<User> u = links.resolveUser(chatId);
        if (u.isEmpty()) {
            send(chatId, "🔒 You need to connect first. Get a code on the website (Profile → Connect Telegram).",
                    rows(List.of(btn("🔗 Connect with code", "act:linkcode"))));
            return null;
        }
        return u.get();
    }

    private void resetConv(Session s) { s.step = Step.NONE; s.data.clear(); }

    private static BigDecimal parseAmount(String s) {
        try { BigDecimal b = new BigDecimal(s.replace("$", "").replace(",", "").trim()); return b.signum() > 0 ? b : null; }
        catch (Exception e) { return null; }
    }
    private static int parseInt(String s) { try { return Integer.parseInt(s); } catch (Exception e) { return 0; } }
    private static long parseLong(String s) { try { return Long.parseLong(s); } catch (Exception e) { return 0; } }

    private static InlineKeyboardButton btn(String text, String data) {
        return InlineKeyboardButton.builder().text(text).callbackData(data).build();
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
