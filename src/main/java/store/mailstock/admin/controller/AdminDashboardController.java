package store.mailstock.admin.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

import store.mailstock.auth.entity.Role;
import store.mailstock.auth.repo.UserRepository;
import store.mailstock.common.dto.ApiResponse;
import store.mailstock.inventory.entity.InventoryItem;
import store.mailstock.inventory.repo.InventoryRepository;
import store.mailstock.order.entity.Order;
import store.mailstock.order.repo.OrderRepository;
import store.mailstock.submission.entity.SellerSubmission;
import store.mailstock.submission.repo.SellerSubmissionRepository;
import store.mailstock.support.entity.SupportTicket;
import store.mailstock.support.repo.SupportTicketRepository;
import store.mailstock.warranty.entity.WarrantyClaim;
import store.mailstock.warranty.repo.WarrantyClaimRepository;
import store.mailstock.wallet.entity.WalletDeposit;
import store.mailstock.wallet.entity.WithdrawRequest;
import store.mailstock.wallet.repo.WalletDepositRepository;
import store.mailstock.wallet.repo.WithdrawRequestRepository;
import store.mailstock.review.repo.ReviewRepository;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final InventoryRepository inventory;
    private final OrderRepository orders;
    private final SellerSubmissionRepository submissions;
    private final WarrantyClaimRepository warranty;
    private final SupportTicketRepository tickets;
    private final WithdrawRequestRepository withdraws;
    private final WalletDepositRepository deposits;
    private final ReviewRepository reviews;
    private final UserRepository users;

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> dashboard() {
        Map<String, Object> m = new LinkedHashMap<>();
        Instant monthAgo = Instant.now().minus(30, ChronoUnit.DAYS);

        BigDecimal revenue = orders.revenueSince(monthAgo);
        BigDecimal profit = inventory.sumProfitSold();

        m.put("revenue_30d", revenue);
        m.put("profit_lifetime", profit);
        m.put("inventory_available", inventory.countByStockStatus(InventoryItem.Status.AVAILABLE));
        m.put("inventory_sold", inventory.countByStockStatus(InventoryItem.Status.SOLD));
        m.put("orders_pending", orders.countByStatus(Order.Status.PENDING_PAYMENT)
                + orders.countByStatus(Order.Status.AWAITING_VERIFICATION));
        m.put("orders_completed", orders.countByStatus(Order.Status.DELIVERED));
        m.put("submissions_pending", submissions.countByStatus(SellerSubmission.Status.PENDING));
        m.put("warranty_open", warranty.countByStatus(WarrantyClaim.Status.OPEN)
                + warranty.countByStatus(WarrantyClaim.Status.PENDING));
        m.put("tickets_open", tickets.countByStatus(SupportTicket.Status.OPEN)
                + tickets.countByStatus(SupportTicket.Status.PENDING));
        m.put("withdrawals_pending", withdraws.countByStatus(WithdrawRequest.Status.PENDING));
        m.put("users_total", users.count());
        m.put("sellers_total", users.findAll().stream().filter(u -> u.hasRole(Role.SELLER)).count());
        m.put("buyers_total", users.findAll().stream().filter(u -> u.hasRole(Role.BUYER)).count());
        return ApiResponse.ok(m);
    }

    /** Lightweight pending counts for the admin sidebar badges (e.g. "Deposits (2)"). */
    @GetMapping("/nav-counts")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, Long>> navCounts() {
        Map<String, Long> m = new LinkedHashMap<>();
        m.put("submissions", submissions.countByStatus(SellerSubmission.Status.PENDING));
        m.put("deposits", deposits.countByStatus(WalletDeposit.Status.PENDING));
        m.put("withdrawals", withdraws.countByStatus(WithdrawRequest.Status.PENDING));
        m.put("warranty", warranty.countByStatus(WarrantyClaim.Status.OPEN)
                + warranty.countByStatus(WarrantyClaim.Status.PENDING));
        m.put("support", tickets.countByStatus(SupportTicket.Status.OPEN)
                + tickets.countByStatus(SupportTicket.Status.PENDING));
        m.put("reviews", reviews.countByApprovedFalse());
        return ApiResponse.ok(m);
    }
}
