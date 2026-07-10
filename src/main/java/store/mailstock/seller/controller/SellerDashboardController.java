package store.mailstock.seller.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

import store.mailstock.common.dto.ApiResponse;
import store.mailstock.common.util.SecurityUtils;
import store.mailstock.submission.entity.SellerSubmission;
import store.mailstock.submission.repo.SellerSubmissionRepository;
import store.mailstock.wallet.entity.Wallet;
import store.mailstock.wallet.service.WalletService;

@RestController
@RequestMapping("/api/seller")
@RequiredArgsConstructor
public class SellerDashboardController {

    private final SellerSubmissionRepository submissions;
    private final WalletService wallet;

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('SELLER')")
    public ApiResponse<Map<String, Object>> dashboard() {
        Long uid = SecurityUtils.currentUserId();
        Wallet w = wallet.get(uid);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total_submitted", submissions.countBySellerId(uid));
        m.put("pending", submissions.countBySellerIdAndStatus(uid, SellerSubmission.Status.PENDING));
        m.put("accepted", submissions.countBySellerIdAndStatus(uid, SellerSubmission.Status.PURCHASED));
        m.put("rejected", submissions.countBySellerIdAndStatus(uid, SellerSubmission.Status.REJECTED));
        m.put("needs_modify", submissions.countBySellerIdAndStatus(uid, SellerSubmission.Status.NEEDS_MODIFY));
        m.put("counter_offered", submissions.countBySellerIdAndStatus(uid, SellerSubmission.Status.COUNTER_OFFERED));
        m.put("wallet_available", w.getAvailableBalance());
        m.put("wallet_pending", w.getPendingBalance());
        m.put("total_earnings", w.getTotalEarnings());
        return ApiResponse.ok(m);
    }
}
