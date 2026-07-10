package store.mailstock.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import store.mailstock.auth.entity.User;
import store.mailstock.config.JwtService;

/**
 * The bot's client to MailStock's OWN REST API. It mints a short-lived access token for the
 * linked user (via {@link JwtService}) and calls the real controllers, so all validation,
 * security, coupon logic and the Binance deposit orchestration are reused — no duplication.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BotApiClient {

    private final TelegramProperties props;
    private final JwtService jwt;
    private final ObjectMapper mapper;

    private volatile RestClient client;

    /** Thrown when the API returns an error; the message is safe to show the user. */
    public static class BotApiException extends RuntimeException {
        public BotApiException(String msg) { super(msg); }
    }

    private RestClient client() {
        RestClient c = client;
        if (c == null) {
            c = RestClient.builder().baseUrl(props.getApiBase()).build();
            client = c;
        }
        return c;
    }

    private String tokenFor(User u) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("uid", u.getId());
        claims.put("roles", u.getRoles());
        return jwt.generateAccessToken(u, claims);
    }

    // ---- public browse (no auth) ----
    public JsonNode browse(int page) {
        return getPublic("/api/inventory/browse?page=" + page + "&size=5");
    }

    public JsonNode item(long id) {
        return getPublic("/api/inventory/browse/" + id);
    }

    // ---- authenticated actions (as the linked user) ----
    public JsonNode wallet(User u) {
        return get(u, "/api/wallet/me");
    }

    public JsonNode buy(User u, List<Long> inventoryIds, String couponCode) {
        Map<String, Object> body = new HashMap<>();
        body.put("inventoryIds", inventoryIds);
        if (couponCode != null && !couponCode.isBlank()) body.put("couponCode", couponCode);
        return post(u, "/api/orders", body);
    }

    public JsonNode deposit(User u, BigDecimal amount, String txid) {
        Map<String, Object> body = new HashMap<>();
        body.put("amount", amount);
        body.put("txid", txid);
        return post(u, "/api/wallet/deposit", body);
    }

    public JsonNode submit(User u, Map<String, Object> body) {
        return post(u, "/api/submissions", body);
    }

    public JsonNode myOrders(User u) {
        return get(u, "/api/orders/me?size=5");
    }

    public JsonNode myEmails(User u) {
        return get(u, "/api/orders/me/emails");
    }

    // ---- low-level ----
    private JsonNode getPublic(String path) {
        return unwrap(client().get().uri(path)
                .retrieve().onStatus(c -> c.isError(), (req, res) -> { throw error(res.getBody()); })
                .body(JsonNode.class));
    }

    private JsonNode get(User u, String path) {
        return unwrap(client().get().uri(path)
                .header("Authorization", "Bearer " + tokenFor(u))
                .retrieve().onStatus(c -> c.isError(), (req, res) -> { throw error(res.getBody()); })
                .body(JsonNode.class));
    }

    private JsonNode post(User u, String path, Object body) {
        return unwrap(client().post().uri(path)
                .header("Authorization", "Bearer " + tokenFor(u))
                .body(body)
                .retrieve().onStatus(c -> c.isError(), (req, res) -> { throw error(res.getBody()); })
                .body(JsonNode.class));
    }

    /** Envelope is { success, message, data } — return the data node. */
    private JsonNode unwrap(JsonNode env) {
        if (env == null) throw new BotApiException("Empty response from server");
        return env.path("data");
    }

    private BotApiException error(java.io.InputStream body) {
        try {
            JsonNode env = mapper.readTree(body);
            String msg = env.path("message").asText("");
            return new BotApiException(msg.isBlank() ? "Request failed" : msg);
        } catch (Exception e) {
            return new BotApiException("Request failed");
        }
    }
}
