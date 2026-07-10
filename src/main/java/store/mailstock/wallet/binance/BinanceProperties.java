package store.mailstock.wallet.binance;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Binance auto-credit poller config (prefix {@code app.binance}). Secrets come from env vars only. */
@Component
@ConfigurationProperties(prefix = "app.binance")
@Getter
@Setter
public class BinanceProperties {
    /** Master switch — when false the manual admin-approval deposit flow is unchanged. */
    private boolean enabled = false;
    /** Personal Binance Spot API key with READ permission only. */
    private String apiKey = "";
    private String apiSecret = "";
    private String apiBase = "https://api.binance.com";
    /** Deposit coin to reconcile, e.g. USDT. */
    private String coin = "USDT";
    /** How far back each poll scans Binance deposit history. */
    private long lookbackMinutes = 1440;
    /** Poll cadence in ms (also referenced by the scheduler via ${app.binance.poll-interval-ms}). */
    private long pollIntervalMs = 120_000;
    /** Max allowed gap between the buyer's requested amount and the settled on-chain amount to auto-credit.
     *  Ties a txid claim to what the buyer said they'd send — a defence against claiming another's txid. */
    private BigDecimal amountTolerance = new BigDecimal("0.01");

    /** True only when polling is enabled AND both credentials are present. */
    public boolean isConfigured() {
        return enabled && apiKey != null && !apiKey.isBlank() && apiSecret != null && !apiSecret.isBlank();
    }
}
