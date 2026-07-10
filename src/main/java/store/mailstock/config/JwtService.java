package store.mailstock.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {

    private final SecretKey key;
    private final long accessTtlMs;
    private final long refreshTtlMs;

    public JwtService(
            @Value("${app.security.jwt.secret}") String secret,
            @Value("${app.security.jwt.access-token-ttl-minutes}") long accessMin,
            @Value("${app.security.jwt.refresh-token-ttl-days}") long refreshDays) {
        byte[] bytes = secret.length() >= 43
                ? tryBase64OrRaw(secret)
                : secret.getBytes(StandardCharsets.UTF_8);
        this.key = Keys.hmacShaKeyFor(padTo32(bytes));
        this.accessTtlMs = accessMin * 60_000L;
        this.refreshTtlMs = refreshDays * 24L * 3600_000L;
    }

    private byte[] tryBase64OrRaw(String s) {
        try { return Decoders.BASE64.decode(s); } catch (Exception e) { return s.getBytes(StandardCharsets.UTF_8); }
    }

    private byte[] padTo32(byte[] b) {
        if (b.length >= 32) return b;
        byte[] out = new byte[32];
        System.arraycopy(b, 0, out, 0, b.length);
        return out;
    }

    public String generateAccessToken(UserDetails user, Map<String, Object> claims) {
        return build(user, claims, accessTtlMs, "access");
    }

    public String generateRefreshToken(UserDetails user) {
        return build(user, new HashMap<>(), refreshTtlMs, "refresh");
    }

    private String build(UserDetails user, Map<String, Object> claims, long ttl, String type) {
        Map<String, Object> c = new HashMap<>(claims);
        c.put("type", type);
        Date now = new Date();
        return Jwts.builder()
                .claims(c)
                .subject(user.getUsername())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttl))
                .signWith(key)
                .compact();
    }

    public String extractUsername(String token) { return extract(token, Claims::getSubject); }
    public String extractType(String token) { return extract(token, c -> c.get("type", String.class)); }

    public <T> T extract(String token, Function<Claims, T> fn) {
        Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        return fn.apply(c);
    }

    public boolean isValid(String token, UserDetails user) {
        try {
            String username = extractUsername(token);
            Date exp = extract(token, Claims::getExpiration);
            return username.equals(user.getUsername()) && exp.after(new Date());
        } catch (Exception e) { return false; }
    }

    public long getRefreshTtlMs() { return refreshTtlMs; }
}
