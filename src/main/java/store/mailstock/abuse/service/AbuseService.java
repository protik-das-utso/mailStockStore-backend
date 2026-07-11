package store.mailstock.abuse.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import store.mailstock.audit.service.AuditService;
import store.mailstock.auth.entity.User;
import store.mailstock.auth.repo.UserRepository;
import store.mailstock.notification.service.NotificationService;
import store.mailstock.setting.repo.SettingRepository;
import store.mailstock.wallet.entity.WalletDeposit;
import store.mailstock.wallet.repo.WalletDepositRepository;
import store.mailstock.warranty.repo.WarrantyClaimRepository;

/**
 * Automatic abuse detection. When a buyer crosses an editable threshold — too many lifetime warranty
 * claims, or too many rejected deposits — the account is FLAGGED for admin review and admins are notified.
 * Flagging does NOT lock the account or block login on its own; it is a heads-up so an admin can decide.
 *
 * Thresholds are read from settings ({@code abuse.warranty_claim_limit}, {@code abuse.failed_deposit_limit})
 * so they can be tuned without a redeploy. Each account is flagged (and admins notified) at most once —
 * an already-flagged account is left alone until an admin clears the flag.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AbuseService {

    private final UserRepository users;
    private final WarrantyClaimRepository warrantyClaims;
    private final WalletDepositRepository deposits;
    private final NotificationService notifications;
    private final SettingRepository settings;
    private final AuditService audit;

    /** Called after a buyer opens a warranty claim. Flags them if their lifetime claim count crosses the limit. */
    @Transactional
    public void onWarrantyClaim(Long buyerId) {
        int limit = intSetting("abuse.warranty_claim_limit", 5);
        if (limit <= 0) return; // 0/blank disables the rule
        long claims = warrantyClaims.countByBuyerId(buyerId);
        if (claims >= limit)
            flag(buyerId, "Excessive warranty claims (" + claims + " ≥ " + limit + ")");
    }

    /** Called after a deposit is rejected. Flags the user if their rejected-deposit count crosses the limit. */
    @Transactional
    public void onFailedDeposit(Long userId) {
        int limit = intSetting("abuse.failed_deposit_limit", 5);
        if (limit <= 0) return; // 0/blank disables the rule
        long failed = deposits.countByUserIdAndStatus(userId, WalletDeposit.Status.REJECTED);
        if (failed >= limit)
            flag(userId, "Excessive failed deposits (" + failed + " ≥ " + limit + ")");
    }

    /** Idempotent flag: sets the flag + notifies admins only on the FIRST crossing (skips already-flagged users). */
    private void flag(Long userId, String reason) {
        User u = users.findById(userId).orElse(null);
        if (u == null || u.isFlagged()) return;
        u.setFlagged(true);
        u.setFlaggedReason(reason);
        u.setFlaggedAt(Instant.now());
        users.save(u);
        notifications.notifyAdmins("ABUSE_FLAG", "Account auto-flagged for review",
                "User #" + userId + " (" + u.getEmail() + ") was auto-flagged: " + reason);
        audit.log(null, "ABUSE_AUTOFLAG", "user", String.valueOf(userId),
                "{\"reason\":\"" + reason.replace("\"", "\\\"") + "\"}", null);
        log.info("[ABUSE] auto-flagged user #{} — {}", userId, reason);
    }

    private int intSetting(String key, int fallback) {
        return settings.findById(key)
                .map(s -> { try { return Integer.parseInt(s.getValue().trim()); } catch (RuntimeException e) { return fallback; } })
                .orElse(fallback);
    }
}
