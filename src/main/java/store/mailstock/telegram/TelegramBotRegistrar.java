package store.mailstock.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import store.mailstock.setting.repo.SettingRepository;

/**
 * Registers {@link MailStockBot} with Telegram (long polling) at startup — but only when
 * app.telegram is configured, so the app runs normally without a bot token.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TelegramBotRegistrar {

    private final TelegramProperties props;
    private final TelegramLinkService links;
    private final BotApiClient api;
    private final SettingRepository settings;

    @Value("${app.uploads-dir:./data/uploads}")
    private String uploadsDir;

    private volatile MailStockBot bot;

    @EventListener(ApplicationReadyEvent.class)
    public void register() {
        if (!props.isConfigured()) {
            log.info("[TELEGRAM] bot disabled (app.telegram.enabled={}, token set={})",
                    props.isEnabled(), props.getBotToken() != null && !props.getBotToken().isBlank());
            return;
        }
        try {
            MailStockBot b = new MailStockBot(props.getBotToken(), props.getBotUsername(), links, api,
                    settings, uploadsDir);
            new TelegramBotsApi(DefaultBotSession.class).registerBot(b);
            this.bot = b;
            log.info("[TELEGRAM] bot @{} registered (long polling)", props.getBotUsername());
        } catch (Exception e) {
            log.error("[TELEGRAM] failed to register bot: {}", e.toString());
        }
    }

    /** The live bot instance, or null when disabled/not yet started. */
    public MailStockBot getBot() { return bot; }
}
