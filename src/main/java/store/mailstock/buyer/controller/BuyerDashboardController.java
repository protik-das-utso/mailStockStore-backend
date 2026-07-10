package store.mailstock.buyer.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

import store.mailstock.common.dto.ApiResponse;
import store.mailstock.common.util.SecurityUtils;
import store.mailstock.inventory.entity.InventoryItem;
import store.mailstock.inventory.repo.InventoryRepository;
import store.mailstock.order.entity.Order;
import store.mailstock.order.repo.OrderRepository;
import store.mailstock.submission.entity.SellerSubmission;
import store.mailstock.warranty.entity.WarrantyClaim;
import store.mailstock.warranty.repo.WarrantyClaimRepository;

@RestController
@RequestMapping("/api/buyer")
@RequiredArgsConstructor
public class BuyerDashboardController {

    private final OrderRepository orders;
    private final WarrantyClaimRepository warranty;
    private final InventoryRepository inventory;

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('BUYER')")
    public ApiResponse<Map<String, Object>> dashboard() {
        Long uid = SecurityUtils.currentUserId();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total_orders", orders.findByBuyerIdOrderByIdDesc(uid, org.springframework.data.domain.Pageable.unpaged()).getTotalElements());
        m.put("pending_payments", orders.countByBuyerIdAndStatus(uid, Order.Status.PENDING_PAYMENT)
                + orders.countByBuyerIdAndStatus(uid, Order.Status.AWAITING_VERIFICATION));
        m.put("completed_orders", orders.countByBuyerIdAndStatus(uid, Order.Status.DELIVERED));
        m.put("active_warranty", warranty.countByBuyerIdAndStatus(uid, WarrantyClaim.Status.OPEN)
                + warranty.countByBuyerIdAndStatus(uid, WarrantyClaim.Status.PENDING));
        m.put("available_inventory", inventory.countByStockStatus(InventoryItem.Status.AVAILABLE));
        m.put("available_gmail", inventory.countByStockStatusAndProvider(InventoryItem.Status.AVAILABLE, SellerSubmission.Provider.GMAIL));
        m.put("available_outlook", inventory.countByStockStatusAndProvider(InventoryItem.Status.AVAILABLE, SellerSubmission.Provider.OUTLOOK));
        m.put("available_old", inventory.countByStockStatusAndAccountType(InventoryItem.Status.AVAILABLE, SellerSubmission.AccountType.OLD));
        m.put("available_new", inventory.countByStockStatusAndAccountType(InventoryItem.Status.AVAILABLE, SellerSubmission.AccountType.NEW));
        return ApiResponse.ok(m);
    }
}
