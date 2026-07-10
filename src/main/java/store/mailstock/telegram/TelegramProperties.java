package store.mailstock.telegram;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Telegram bot config (prefix {@code app.telegram}). Secrets come from env vars only. */
@Component
@ConfigurationProperties(prefix = "app.telegram")
@Getter
@Setter
public class TelegramProperties {
    /** Master switch — when false the bot is never registered and the app runs unchanged. */
    private boolean enabled = false;
    /** BotFather token. */
    private String botToken = "";
    /** Bot @username (without the @), used to build t.me deep links. */
    private String botUsername = "";
    /** Base URL of this app's own REST API that the bot calls on the user's behalf.
     *  Value comes solely from the TELEGRAM_API_BASE env var (wired in application.yml) —
     *  no hardcoded URL here. */
    private String apiBase;

    /** True only when enabled AND a token is present. */
    public boolean isConfigured() {
        return enabled && botToken != null && !botToken.isBlank();
    }
}
