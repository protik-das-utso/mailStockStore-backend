package store.mailstock.recovery.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import store.mailstock.audit.service.AuditService;
import store.mailstock.common.dto.ApiResponse;
import store.mailstock.recovery.dto.RecoveryCodeEntry;
import store.mailstock.recovery.service.RecoveryCodeService;

/**
 * Public (no-auth) recovery-code lookup. The unguessable token in the path IS the credential; it is
 * scoped to a single account, so it can only ever return that account's latest Google verification code.
 * Rate-limited by {@code RateLimitFilter} and audited on every hit. Lives under {@code /api/public/**},
 * which SecurityConfig permits for GET.
 */
@RestController
@RequestMapping("/api/public/recovery")
@RequiredArgsConstructor
@Slf4j
public class RecoveryPublicController {

    private final RecoveryCodeService codeService;
    private final AuditService audit;

    @GetMapping("/{token}")
    public ApiResponse<List<RecoveryCodeEntry>> getCodes(@PathVariable String token, HttpServletRequest req) {
        List<RecoveryCodeEntry> codes = codeService.codesForToken(token);
        // Audit the fact of a fetch (never the codes themselves) so a disputed account has a trail.
        audit.log(null, "RECOVERY_CODE_VIEW", "recovery_link", token, null, clientIp(req));
        return ApiResponse.ok(codes);
    }

    private static String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        return (xff != null && !xff.isBlank()) ? xff.split(",")[0].trim() : req.getRemoteAddr();
    }
}
