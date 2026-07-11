package store.mailstock.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Minimal client for the Resend transactional email HTTP API, using the JDK HttpClient (no extra deps).
 * Sends over HTTPS rather than SMTP — Railway's Hobby plan blocks outbound SMTP on any host/port.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ResendClient {

    private static final String SEND_PATH = "/emails";

    private final ResendProperties props;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    /** Sends one HTML email. Returns true on success (HTTP 2xx from Resend). */
    public boolean send(String fromHeader, String to, String subject, String html) {
        if (!props.isConfigured()) {
            log.warn("[RESEND] api key not configured — skipping send to {}", to);
            return false;
        }
        try {
            String body = mapper.writeValueAsString(Map.of(
                    "from", fromHeader,
                    "to", to,
                    "subject", subject,
                    "html", html
            ));
            HttpRequest req = HttpRequest.newBuilder(URI.create(props.getApiBase() + SEND_PATH))
                    .header("Authorization", "Bearer " + props.getApiKey())
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) {
                log.error("[RESEND] send to {} failed: HTTP {} - {}", to, res.statusCode(), res.body());
                return false;
            }
            return true;
        } catch (Exception e) {
            log.error("[RESEND] send to {} failed: {}", to, e.toString());
            return false;
        }
    }
}
