package store.mailstock.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

import store.mailstock.setting.repo.SettingRepository;

/**
 * Best-effort mirror of in-app notifications to Telegram, for users who linked their chat.
 * Never throws into the caller — a Telegram hiccup must not break the DB notification.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TelegramNotifier {

    private final TelegramProperties props;
    private final TelegramLinkRepository links;
    private final TelegramBotRegistrar registrar;
    private final SettingRepository settings;

    /** Chat id(s) that receive admin alerts even without linking the bot. Comma-separated. */
    static final String ADMIN_CHAT_KEY = "telegram.admin_chat_id";

    public void push(Long userId, String title, String body) {
        if (userId == null) return;
        MailStockBot bot = registrar.getBot();
        if (bot == null || !registrar.isRunning()) return;
        try {
            links.findByUserId(userId).ifPresent(l ->
                    bot.pushMessage(l.getChatId(), "*" + safe(title) + "*\n" + safe(body)));
        } catch (Exception e) {
            log.debug("[TELEGRAM] notify push skipped: {}", e.toString());
        }
    }

    /**
     * Broadcast to every linked chat (e.g. a new announcement). Best-effort: a failure to one chat
     * (blocked bot, deleted account) is logged and the rest still receive the message.
     * If {@code userIds} is non-null, only those users' linked chats are messaged (audience targeting).
     */
    public void broadcast(String title, String body, java.util.Collection<Long> userIds) {
        MailStockBot bot = registrar.getBot();
        if (bot == null || !registrar.isRunning()) return;
        String text = "*" + safe(title) + "*\n" + safe(body);
        for (TelegramLink l : links.findAll()) {
            if (userIds != null && !userIds.contains(l.getUserId())) continue;
            try {
                bot.pushMessage(l.getChatId(), text);
            } catch (Exception e) {
                log.debug("[TELEGRAM] broadcast to chat {} skipped: {}", l.getChatId(), e.toString());
            }
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s.replace("*", "\\*").replace("_", "\\_").replace("`", "\\`");
    }

    /**
     * Alert the admin's Telegram about a platform event (new registration, new submission…), gated
     * by the per-event {@code telegram.alert.<event>} setting (default ON when never touched).
     * Goes only to the chat id(s) in the {@code telegram.admin_chat_id} setting — admins who linked
     * the bot to their account already get the in-app mirror, so this covers the owner's personal
     * chat without double-sending. Best-effort: never throws into the caller.
     */
    public void adminAlert(String event, String title, String body) {
        try {
            if (!alertEnabled(event)) return;
            MailStockBot bot = registrar.getBot();
            if (bot == null || !registrar.isRunning()) return;
            String text = "🔔 *" + safe(title) + "*\n" + safe(body);
            for (Long chatId : adminChatIds()) {
                try {
                    bot.pushMessage(chatId, text);
                } catch (Exception e) {
                    log.debug("[TELEGRAM] admin alert to chat {} skipped: {}", chatId, e.toString());
                }
            }
        } catch (Exception e) {
            log.debug("[TELEGRAM] admin alert skipped: {}", e.toString());
        }
    }

    /** Per-event switch, editable from admin settings. Missing/blank setting = ON. */
    private boolean alertEnabled(String event) {
        return settings.findById("telegram.alert." + event.toLowerCase())
                .map(s -> s.getValue() == null || s.getValue().isBlank()
                        || "true".equalsIgnoreCase(s.getValue().trim()))
                .orElse(true);
    }

    /** Chat id(s) from the {@code telegram.admin_chat_id} setting (comma/space separated). */
    private Set<Long> adminChatIds() {
        Set<Long> ids = new LinkedHashSet<>();
        settings.findById(ADMIN_CHAT_KEY).ifPresent(s -> {
            String v = s.getValue() == null ? "" : s.getValue();
            for (String part : v.split("[,;\\s]+")) {
                try { if (!part.isBlank()) ids.add(Long.parseLong(part.trim())); }
                catch (NumberFormatException ignored) { /* skip bad entry, keep the rest */ }
            }
        });
        return ids;
    }
}
