package store.mailstock.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-client, per-endpoint rate limiting (bucket4j, in-memory).
 *
 * Applies to:
 *  - all {@code /api/auth/**} requests (login/register/refresh/reset), at {@code auth-requests-per-minute};
 *  - buyer money mutations {@code POST /api/wallet/deposit} and {@code POST /api/wallet/withdraw},
 *    at the stricter {@code sensitive-requests-per-minute}.
 *
 * The bucket map is swept periodically so idle client entries don't accumulate unbounded.
 * Note: this is per-instance only. Behind a load balancer or with multiple replicas, back the
 * buckets with a shared store (e.g. bucket4j-redis) so the limit is enforced across instances.
 */
@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    /** How long an idle bucket is kept before the sweeper evicts it. */
    private static final Duration BUCKET_TTL = Duration.ofMinutes(10);

    private final ConcurrentHashMap<String, Entry> buckets = new ConcurrentHashMap<>();
    private final int authPerMinute;
    private final int sensitivePerMinute;

    public RateLimitFilter(
            @Value("${app.rate-limit.auth-requests-per-minute:20}") int authPerMinute,
            @Value("${app.rate-limit.sensitive-requests-per-minute:10}") int sensitivePerMinute) {
        this.authPerMinute = authPerMinute;
        this.sensitivePerMinute = sensitivePerMinute;
    }

    /** Bucket plus last-access time, so the sweeper can evict idle clients. */
    private static final class Entry {
        final Bucket bucket;
        volatile long lastSeenMs;
        Entry(Bucket bucket, long nowMs) { this.bucket = bucket; this.lastSeenMs = nowMs; }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        return limitFor(req) == 0;
    }

    /** Returns the per-minute limit for this request, or 0 if it should not be rate limited. */
    private int limitFor(HttpServletRequest req) {
        String uri = req.getRequestURI();
        if (uri.startsWith("/api/auth/")) return authPerMinute;
        // Public, unauthenticated recovery-code lookups hit an external mailbox over POP3 — cap them
        // per client so the link can't be hammered into a code-scraping / mailbox-DoS tool.
        if (uri.startsWith("/api/public/recovery/")) return sensitivePerMinute;
        if ("POST".equals(req.getMethod())
                && (uri.equals("/api/wallet/deposit") || uri.equals("/api/wallet/withdraw"))) {
            return sensitivePerMinute;
        }
        return 0;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest req, @NonNull HttpServletResponse res,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        int perMinute = limitFor(req);
        String key = req.getRemoteAddr() + ":" + req.getRequestURI();
        long now = System.currentTimeMillis();
        Entry entry = buckets.compute(key, (k, existing) -> {
            if (existing != null) { existing.lastSeenMs = now; return existing; }
            Bucket bucket = Bucket.builder()
                    .addLimit(Bandwidth.builder().capacity(perMinute)
                            .refillGreedy(perMinute, Duration.ofMinutes(1)).build())
                    .build();
            return new Entry(bucket, now);
        });

        if (entry.bucket.tryConsume(1)) {
            chain.doFilter(req, res);
        } else {
            res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            res.setContentType("application/json");
            res.getWriter().write("{\"success\":false,\"message\":\"Rate limit exceeded\"}");
        }
    }

    /** Evict buckets not touched within {@link #BUCKET_TTL}, bounding memory use. */
    @Scheduled(fixedDelay = 5 * 60_000L, initialDelay = 5 * 60_000L)
    void evictIdleBuckets() {
        long cutoff = System.currentTimeMillis() - BUCKET_TTL.toMillis();
        buckets.values().removeIf(e -> e.lastSeenMs < cutoff);
    }
}
