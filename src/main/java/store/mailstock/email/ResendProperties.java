package store.mailstock.email;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Resend transactional email API config (prefix {@code app.resend}). API key is env-only. */
@Component
@ConfigurationProperties(prefix = "app.resend")
@Getter
@Setter
public class ResendProperties {
    private String apiKey = "";
    private String apiBase = "https://api.resend.com";

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
