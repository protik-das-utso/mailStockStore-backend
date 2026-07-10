package store.mailstock.warranty.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import store.mailstock.audit.service.AuditService;
import store.mailstock.common.exception.ApiException;
import store.mailstock.inventory.entity.InventoryItem;
import store.mailstock.inventory.repo.InventoryRepository;
import store.mailstock.notification.service.NotificationService;
import store.mailstock.order.dto.ManualDeliverRequest;
import store.mailstock.order.entity.OrderItem;
import store.mailstock.order.repo.OrderItemRepository;
import store.mailstock.order.service.OrderService;
import store.mailstock.warranty.dto.WarrantyCreateRequest;
import store.mailstock.warranty.dto.WarrantyDecisionRequest;
import store.mailstock.warranty.entity.WarrantyClaim;
import store.mailstock.warranty.repo.WarrantyClaimRepository;
import store.mailstock.wallet.service.WalletService;

@Service
@RequiredArgsConstructor
public class WarrantyService {

    private final WarrantyClaimRepository repo;
    private final OrderItemRepository orderItems;
    private final InventoryRepository inventory;
    private final NotificationService notifications;
    private final WalletService wallet;
    private final OrderService orderService;
    private final AuditService audit;

    @Transactional
    public WarrantyClaim open(Long buyerId, WarrantyCreateRequest req) {
        OrderItem oi = orderItems.findById(req.orderItemId())
                .orElseThrow(() -> ApiException.notFound("Order item not found"));
        if (oi.getWarrantyExpiresAt() != null && oi.getWarrantyExpiresAt().isBefore(Instant.now()))
            throw ApiException.badRequest("Warranty period expired");
        WarrantyClaim c = WarrantyClaim.builder()
                .orderItemId(oi.getId()).buyerId(buyerId)
                .reason(req.reason()).description(req.description()).evidenceUrl(req.evidenceUrl())
                .build();
        c = repo.save(c);
        notifications.notifyAdmins("NEW_WARRANTY", "New warranty claim", "Claim #" + c.getId());
        return c;
    }

    @Transactional(readOnly = true)
    public Page<WarrantyClaim> mine(Long buyerId, Pageable p) { return repo.findByBuyerIdOrderByIdDesc(buyerId, p); }

    @Transactional(readOnly = true)
    public Page<WarrantyClaim> adminList(WarrantyClaim.Status status, String q, Pageable p) {
        return repo.adminSearch(status, (q != null && !q.isBlank()) ? q.trim() : null, p);
    }

    @Transactional
    public WarrantyClaim decide(Long adminId, Long id, WarrantyDecisionRequest req) {
        WarrantyClaim c = repo.findById(id).orElseThrow(() -> ApiException.notFound("Claim not found"));
        if (c.getStatus() == WarrantyClaim.Status.RESOLVED || c.getStatus() == WarrantyClaim.Status.CLOSED)
            throw ApiException.badRequest("Claim already finalized");

        OrderItem oi = orderItems.findById(c.getOrderItemId())
                .orElseThrow(() -> ApiException.notFound("Order item not found"));
        InventoryItem original = inventory.findById(oi.getInventoryId()).orElse(null);

        c.setAdminNote(req.adminNote());
        c.setResolvedBy(adminId);
        c.setResolvedAt(Instant.now());

        String res = req.resolution();
        switch (res) {
            case "REPLACE" -> {
                c.setResolution(WarrantyClaim.Resolution.REPLACE);
                c.setStatus(WarrantyClaim.Status.RESOLVED);
                autoReplace(adminId, c, original);
            }
            case "REFUND" -> {
                c.setResolution(WarrantyClaim.Resolution.REFUND);
                c.setStatus(WarrantyClaim.Status.RESOLVED);
                wallet.refundBuyer(c.getBuyerId(), oi.getPrice(), "warranty:" + c.getId());
                notifications.notify(c.getBuyerId(), "WARRANTY_REFUND", "Warranty refund",
                        "Claim #" + c.getId() + ": " + oi.getPrice() + " was refunded to your balance.");
            }
            case "REJECT" -> { c.setResolution(WarrantyClaim.Resolution.REJECT); c.setStatus(WarrantyClaim.Status.CLOSED); }
            case "CLOSE"  -> c.setStatus(WarrantyClaim.Status.CLOSED);
            default -> throw ApiException.badRequest("Unknown resolution");
        }

        // Seller accountability: the account is dead, so claw back the seller's payout and warn them.
        // Skip entirely when the item had no seller (admin added it personally).
        if ((res.equals("REPLACE") || res.equals("REFUND")) && original != null && original.getSellerId() != null) {
            BigDecimal pct = clawbackPct(req.sellerClawback());
            if (pct.signum() > 0) {
                BigDecimal amount = original.getPurchasePrice().multiply(pct);
                wallet.clawbackSeller(original.getSellerId(), amount, "warranty:" + c.getId(),
                        "Dead-account clawback: " + original.getTitle());
                notifications.notify(original.getSellerId(), "ACCOUNT_DEAD", "Account reported dead",
                        "The account \"" + original.getTitle() + "\" you supplied was reported dead/disabled. "
                                + amount + " was deducted from your balance.");
            }
        }

        notifications.notify(c.getBuyerId(), "WARRANTY_UPDATED",
                "Warranty updated", "Claim #" + c.getId() + " → " + c.getStatus());
        audit.log(adminId, "WARRANTY_DECIDE", "warranty", String.valueOf(id),
                "{\"resolution\":\"" + res + "\",\"clawback\":\"" + (req.sellerClawback() == null ? "NONE" : req.sellerClawback())
                        + "\",\"seller\":" + (original != null ? original.getSellerId() : null) + "}", null);
        return repo.save(c);
    }

    /** Auto-deliver a fresh AVAILABLE email of the same provider + type; notify admin if none in stock. */
    private void autoReplace(Long adminId, WarrantyClaim c, InventoryItem original) {
        Optional<InventoryItem> repl = (original == null || original.getAccountType() == null)
                ? Optional.empty()
                : inventory.findFirstByStockStatusAndProviderAndAccountTypeOrderByIdDesc(
                        InventoryItem.Status.AVAILABLE, original.getProvider(), original.getAccountType());
        if (repl.isPresent()) {
            orderService.manualDeliver(adminId,
                    new ManualDeliverRequest(c.getBuyerId(), repl.get().getId(), false, "warranty:" + c.getId()));
            notifications.notify(c.getBuyerId(), "WARRANTY_REPLACED", "Replacement delivered",
                    "Claim #" + c.getId() + ": a replacement email was delivered to your account.");
        } else {
            notifications.notifyAdmins("WARRANTY_MANUAL", "Manual replacement needed",
                    "Claim #" + c.getId() + ": no matching stock" + (original != null
                            ? " (" + original.getProvider() + " " + original.getAccountType() + ")" : "")
                            + " — deliver a replacement manually once restocked.");
            notifications.notify(c.getBuyerId(), "WARRANTY_REPLACED", "Replacement pending",
                    "Claim #" + c.getId() + " approved. Your replacement email will be delivered shortly.");
        }
    }

    private static BigDecimal clawbackPct(String s) {
        if (s == null) return BigDecimal.ZERO;
        return switch (s) {
            case "FULL" -> BigDecimal.ONE;
            case "HALF" -> new BigDecimal("0.50");
            case "QUARTER" -> new BigDecimal("0.25");
            default -> BigDecimal.ZERO;
        };
    }
}
