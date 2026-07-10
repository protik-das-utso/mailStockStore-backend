package store.mailstock.wallet.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import store.mailstock.common.dto.ApiResponse;
import store.mailstock.common.dto.PageResponse;
import store.mailstock.common.util.SecurityUtils;
import store.mailstock.wallet.dto.AdminCreditRequest;
import store.mailstock.wallet.dto.WithdrawCreateRequest;
import store.mailstock.wallet.dto.WithdrawDecisionRequest;
import store.mailstock.wallet.entity.Wallet;
import store.mailstock.wallet.entity.WalletTransaction;
import store.mailstock.wallet.entity.WalletDeposit;
import store.mailstock.wallet.entity.WithdrawRequest;
import store.mailstock.wallet.binance.BinanceDepositReconciler;
import store.mailstock.wallet.service.WalletService;

@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
@Slf4j
public class WalletController {

    private final WalletService wallet;
    private final BinanceDepositReconciler binanceValidator;

    @GetMapping("/me")
    @PreAuthorize("hasRole('SELLER') or hasRole('BUYER') or hasRole('ADMIN')")
    public ApiResponse<Wallet> me() { return ApiResponse.ok(wallet.get(SecurityUtils.currentUserId())); }

    @GetMapping("/me/transactions")
    @PreAuthorize("hasRole('SELLER') or hasRole('BUYER') or hasRole('ADMIN')")
    public ApiResponse<PageResponse<WalletTransaction>> transactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<WalletTransaction> p = wallet.transactions(SecurityUtils.currentUserId(), PageRequest.of(page, size));
        return ApiResponse.ok(PageResponse.of(p));
    }

    // ---- Buyer balance deposits ----
    @PostMapping("/deposit")
    @PreAuthorize("hasRole('BUYER')")
    public ApiResponse<WalletDeposit> deposit(@Valid @RequestBody store.mailstock.wallet.dto.DepositCreateRequest req) {
        WalletDeposit d = wallet.requestDeposit(SecurityUtils.currentUserId(), req);
        log.info("[DEPOSIT] submitted #{} user={} amount={} txid={} -> attempting instant Binance validation",
                d.getId(), d.getUserId(), d.getAmount(), d.getTxid());
        // Try to validate against Binance Pay immediately; if the payment isn't visible yet the
        // scheduled reconciler will pick it up. Never fail the request on a validation hiccup.
        try {
            boolean credited = binanceValidator.validateNow(d);
            log.info("[DEPOSIT] instant validation for #{} result: {}", d.getId(),
                    credited ? "CREDITED (approved)" : "not credited yet — left PENDING for poller/admin");
        } catch (Exception e) {
            log.warn("[DEPOSIT] instant validation for #{} threw {} — left PENDING", d.getId(), e.toString());
        }
        return ApiResponse.ok(wallet.getDeposit(d.getId()));
    }

    @GetMapping("/deposit/me")
    @PreAuthorize("hasRole('BUYER')")
    public ApiResponse<PageResponse<store.mailstock.wallet.entity.WalletDeposit>> myDeposits(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.of(wallet.myDeposits(SecurityUtils.currentUserId(), PageRequest.of(page, size))));
    }

    @GetMapping("/admin/deposits")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResponse<store.mailstock.wallet.entity.WalletDeposit>> adminDeposits(
            @RequestParam(required = false) store.mailstock.wallet.entity.WalletDeposit.Status status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.of(wallet.adminDeposits(status, q, PageRequest.of(page, size))));
    }

    @PostMapping("/admin/deposits/{id}/decision")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<store.mailstock.wallet.entity.WalletDeposit> decideDeposit(
            @PathVariable Long id, @Valid @RequestBody store.mailstock.wallet.dto.DepositDecisionRequest req) {
        return ApiResponse.ok(wallet.decideDeposit(SecurityUtils.currentUserId(), id, req));
    }

    /** Admin adds balance to any user directly (records a ledger transaction + audit log). */
    @PostMapping("/admin/users/{userId}/credit")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Wallet> adminCredit(@PathVariable Long userId, @Valid @RequestBody AdminCreditRequest req) {
        return ApiResponse.ok(wallet.adminCredit(SecurityUtils.currentUserId(), userId, req.amount(), req.note()));
    }

    @PostMapping("/withdraw")
    @PreAuthorize("hasRole('SELLER')")
    public ApiResponse<WithdrawRequest> withdraw(@Valid @RequestBody WithdrawCreateRequest req) {
        return ApiResponse.ok(wallet.requestWithdraw(SecurityUtils.currentUserId(), req));
    }

    @GetMapping("/withdraw/me")
    @PreAuthorize("hasRole('SELLER')")
    public ApiResponse<PageResponse<WithdrawRequest>> myWithdrawals(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.of(wallet.myWithdrawals(SecurityUtils.currentUserId(), PageRequest.of(page, size))));
    }

    // Admin endpoints
    @GetMapping("/admin/withdrawals")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResponse<WithdrawRequest>> adminList(
            @RequestParam(required = false) WithdrawRequest.Status status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.of(wallet.adminList(status, q, PageRequest.of(page, size))));
    }

    @PostMapping("/admin/withdrawals/{id}/decision")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<WithdrawRequest> decide(@PathVariable Long id, @Valid @RequestBody WithdrawDecisionRequest req) {
        return ApiResponse.ok(wallet.decide(SecurityUtils.currentUserId(), id, req));
    }
}
