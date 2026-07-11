package store.mailstock.seller.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

import store.mailstock.common.dto.ApiResponse;
import store.mailstock.common.util.SecurityUtils;
import store.mailstock.inventory.entity.InventoryItem;
import store.mailstock.inventory.repo.InventoryRepository;
import store.mailstock.submission.entity.SellerSubmission;
import store.mailstock.submission.repo.SellerSubmissionRepository;
import store.mailstock.wallet.entity.Wallet;
import store.mailstock.wallet.service.WalletService;

@RestController
@RequestMapping("/api/seller")
@RequiredArgsConstructor
public class SellerDashboardController {

    private final SellerSubmissionRepository submissions;
    private final InventoryRepository inventory;
    private final WalletService wallet;

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('SELLER')")
    public ApiResponse<Map<String, Object>> dashboard() {
        Long uid = SecurityUtils.currentUserId();
        Wallet w = wallet.get(uid);
        Map<String, Object> m = new LinkedHashMap<>();
        // Submission pipeline
        m.put("total_submitted", submissions.countBySellerId(uid));
        m.put("pending", submissions.countBySellerIdAndStatus(uid, SellerSubmission.Status.PENDING));
        m.put("approved", submissions.countBySellerIdAndStatus(uid, SellerSubmission.Status.APPROVED));
        m.put("in_inventory", submissions.countBySellerIdAndStatus(uid, SellerSubmission.Status.PURCHASED));
        m.put("accepted", submissions.countBySellerIdAndStatus(uid, SellerSubmission.Status.PURCHASED)); // legacy alias
        m.put("rejected", submissions.countBySellerIdAndStatus(uid, SellerSubmission.Status.REJECTED));
        m.put("needs_modify", submissions.countBySellerIdAndStatus(uid, SellerSubmission.Status.NEEDS_MODIFY));
        m.put("counter_offered", submissions.countBySellerIdAndStatus(uid, SellerSubmission.Status.COUNTER_OFFERED));
        // Live account health (from inventory rows sourced from this seller)
        long available = inventory.countBySellerIdAndStockStatus(uid, InventoryItem.Status.AVAILABLE);
        long sold = inventory.countBySellerIdAndStockStatus(uid, InventoryItem.Status.SOLD);
        long dead = inventory.countBySellerIdAndStockStatus(uid, InventoryItem.Status.DEAD);
        m.put("accounts_available", available);        // listed, not yet sold
        m.put("accounts_sold", sold);                  // sold & still working
        m.put("accounts_dead", dead);                  // reported dead via warranty
        m.put("accounts_working", available + sold);   // alive right now
        // Wallet
        m.put("wallet_available", w.getAvailableBalance());
        m.put("wallet_pending", w.getPendingBalance());
        m.put("total_earnings", w.getTotalEarnings());
        return ApiResponse.ok(m);
    }
}
