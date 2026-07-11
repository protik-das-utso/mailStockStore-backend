package store.mailstock.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
}
