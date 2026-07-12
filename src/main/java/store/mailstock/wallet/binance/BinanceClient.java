package store.mailstock.wallet.binance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import store.mailstock.common.util.MaskUtil;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal signed client for the Binance Spot REST API, using the JDK HttpClient (no extra deps).
 * Reads Binance Pay trade history so the reconciler can auto-credit buyers who paid via the Pay QR.
 * The API key it uses should have READ permission only.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BinanceClient {

    private static final String PAY_HISTORY_PATH = "/sapi/v1/pay/transactions";

    private final BinanceProperties props;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    /** One incoming Binance Pay transaction (income) into the store's account.
     *  {@code orderId} is the value the buyer sees on their receipt and pastes; {@code transactionId} is
     *  Binance's internal id, kept for traceability. */
    public record PayTxn(String orderId, String transactionId, BigDecimal amount, String currency, String orderType, long transactionTime) {}

    /** Incoming ({@code amount > 0}) Binance Pay transactions within the lookback window. */
    public List<PayTxn> recentPayIncome() {
        long now = System.currentTimeMillis();
        long start = now - props.getLookbackMinutes() * 60_000L;
        String query = "startTime=" + start
                + "&endTime=" + now
                + "&recvWindow=60000"
                + "&timestamp=" + now;
        String url = props.getApiBase() + PAY_HISTORY_PATH + "?" + query
                + "&signature=" + hmacSha256(query, props.getApiSecret());

        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("X-MBX-APIKEY", props.getApiKey())
                .timeout(Duration.ofSeconds(20))
                .GET().build();
        log.debug("[BINANCE] GET {} (window last {} min, coin {})", PAY_HISTORY_PATH,
                props.getLookbackMinutes(), props.getCoin());
        try {
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                log.warn("[BINANCE] pay history HTTP {}", res.statusCode());
                log.debug("[BINANCE] error body: {}", res.body());
                return List.of();
            }
            // Raw body can contain full txids/order ids — keep it at DEBUG, never INFO.
            log.debug("[BINANCE] pay history raw response: {}", res.body());
            // Envelope: { "code":"000000", "data":[ {...} ], "success":true }
            JsonNode data = mapper.readTree(res.body()).path("data");
            List<PayTxn> out = new ArrayList<>();
            if (data.isArray()) {
                for (JsonNode n : data) {
                    BigDecimal amount = new BigDecimal(n.path("amount").asText("0"));
                    String orderId = text(n, "orderId");
                    String txId = text(n, "transactionId");
                    if (amount.signum() <= 0) {
                        log.info("[BINANCE]  - skip txn order={} (non-income amount {})",
                                MaskUtil.maskId(orderId), amount);
                        continue; // income only (positive)
                    }
                    if (orderId == null || orderId.isBlank()) {
                        log.info("[BINANCE]  - skip txn with blank orderId (amount {})", amount);
                        continue;
                    }
                    log.info("[BINANCE]  + income orderId={} txnId={} amount={} {} type={}",
                            MaskUtil.maskId(orderId), MaskUtil.maskId(txId), amount,
                            text(n, "currency"), text(n, "orderType"));
                    out.add(new PayTxn(orderId, txId, amount, text(n, "currency"),
                            text(n, "orderType"), n.path("transactionTime").asLong(0)));
                }
            }
            // Only worth an INFO line when we actually found money; otherwise stay at DEBUG so the
            // 2-minute poll doesn't flood the host log with "parsed 0" on every empty cycle.
            if (out.isEmpty()) log.debug("[BINANCE] parsed 0 incoming Pay transaction(s) from history");
            else log.info("[BINANCE] parsed {} incoming Pay transaction(s) from history", out.size());
            return out;
        } catch (Exception e) {
            log.warn("[BINANCE] pay history poll failed: {}", e.toString());
            return List.of();
        }
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return (v == null || v.isNull()) ? null : v.asText();
    }

    static String hmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(raw.length * 2);
            for (byte b : raw) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 signing failed", e);
        }
    }
}
