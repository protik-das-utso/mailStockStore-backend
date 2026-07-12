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
    private final store.mailstock.order.repo.OrderRepository orders;
    private final InventoryRepository inventory;
    private final NotificationService notifications;
    private final WalletService wallet;
    private final OrderService orderService;
    private final AuditService audit;
    private final store.mailstock.abuse.service.AbuseService abuse;
    private final store.mailstock.auth.repo.UserRepository users;

    @Transactional
    public WarrantyClaim open(Long buyerId, WarrantyCreateRequest req) {
        OrderItem oi = orderItems.findById(req.orderItemId())
                .orElseThrow(() -> ApiException.notFound("Order item not found"));
        store.mailstock.order.entity.Order order = orders.findById(oi.getOrderId())
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        if (!order.getBuyerId().equals(buyerId))
            throw ApiException.forbidden("Not your account");
        // Payment must have cleared first: an order still awaiting (or failed) payment can never claim warranty.
        store.mailstock.order.entity.Order.Status st = order.getStatus();
        if (st == store.mailstock.order.entity.Order.Status.PENDING_PAYMENT
                || st == store.mailstock.order.entity.Order.Status.AWAITING_VERIFICATION)
            throw ApiException.badRequest("Payment for this order is still pending — you can claim warranty only after it is paid and delivered.");
        if (st != store.mailstock.order.entity.Order.Status.DELIVERED)
            throw ApiException.badRequest("Warranty can only be claimed on a delivered order.");
        // A null expiry means the account was sold with no warranty at all (0-day category, or an item
        // delivered before expiries were always stamped). It is NOT an "unlimited warranty".
        if (oi.getWarrantyExpiresAt() == null)
            throw ApiException.badRequest("This account was sold without a warranty, so it cannot be claimed.");
        if (oi.getWarrantyExpiresAt().isBefore(Instant.now()))
            throw ApiException.badRequest("Warranty period expired");
        // One claim per email — for life. Once ANY claim (even a rejected one) has been opened against
        // this account, it can never be claimed again. A fresh replacement is a NEW order item with its
        // own warranty, so it remains independently claimable.
        if (!repo.findByOrderItemId(oi.getId()).isEmpty())
            throw ApiException.badRequest("A warranty claim has already been made for this account — only one claim per email is allowed.");
        WarrantyClaim c = WarrantyClaim.builder()
                .orderItemId(oi.getId()).buyerId(buyerId)
                .reason(req.reason()).description(req.description()).evidenceUrl(req.evidenceUrl())
                .build();
        c = repo.save(c);
        notifications.notifyAdmins("NEW_WARRANTY", "New warranty claim", "Claim #" + c.getId());
        // Auto-flag the buyer for admin review if this claim pushes them over the abuse threshold.
        abuse.onWarrantyClaim(buyerId);
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
                if (req.replacementInventoryId() != null)
                    deliverSpecificReplacement(adminId, c, req.replacementInventoryId());
                else
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

        // A replaced/refunded account is confirmed dead: flag the inventory item DEAD (so the seller's
        // dashboard counts it) and hold the seller accountable.
        if ((res.equals("REPLACE") || res.equals("REFUND")) && original != null) {
            original.setStockStatus(InventoryItem.Status.DEAD);
            inventory.save(original);

            // Notify the seller their account died — always, so they see it in-app AND on Telegram
            // (notify() mirrors to the bot when the chat is linked). Skip only when there's no seller.
            if (original.getSellerId() != null) {
                BigDecimal pct = clawbackPct(req.sellerClawback());
                String clawbackMsg = "";
                if (pct.signum() > 0) {
                    BigDecimal amount = original.getPurchasePrice().multiply(pct);
                    wallet.clawbackSeller(original.getSellerId(), amount, "warranty:" + c.getId(),
                            "Dead-account clawback: " + original.getTitle());
                    clawbackMsg = " " + amount + " was deducted from your balance.";
                }
                notifications.notify(original.getSellerId(), "ACCOUNT_DEAD", "Account reported dead",
                        "The account \"" + original.getTitle() + "\" you supplied was reported dead/disabled and marked DEAD."
                                + clawbackMsg);
            }
        }

        notifications.notify(c.getBuyerId(), "WARRANTY_UPDATED",
                "Warranty updated", "Claim #" + c.getId() + " → " + c.getStatus());
        audit.log(adminId, "WARRANTY_DECIDE", "warranty", String.valueOf(id),
                "{\"resolution\":\"" + res + "\",\"clawback\":\"" + (req.sellerClawback() == null ? "NONE" : req.sellerClawback())
                        + "\",\"seller\":" + (original != null ? original.getSellerId() : null) + "}", null);
        return repo.save(c);
    }

    /** Admin picked an exact account to hand over: validate it's AVAILABLE, then deliver it. */
    private void deliverSpecificReplacement(Long adminId, WarrantyClaim c, Long inventoryId) {
        InventoryItem repl = inventory.findById(inventoryId)
                .orElseThrow(() -> ApiException.notFound("Replacement account not found"));
        if (repl.getStockStatus() != InventoryItem.Status.AVAILABLE)
            throw ApiException.badRequest("That replacement account is no longer available: " + repl.getTitle());
        orderService.manualDeliver(adminId,
                new ManualDeliverRequest(c.getBuyerId(), repl.getId(), false, "warranty:" + c.getId()));
        notifications.notify(c.getBuyerId(), "WARRANTY_REPLACED", "Replacement delivered",
                "Claim #" + c.getId() + ": a replacement account was delivered — open \"My emails\" to view its credentials.");
    }

    /**
     * Read-only detail for the admin panel: the claim, its buyer, the purchased (dead) account with
     * credentials, the original inventory row, and available stock the admin can hand over. Candidates
     * prioritise the same provider+category and fall back to any available account.
     */
    @Transactional(readOnly = true)
    public store.mailstock.warranty.dto.WarrantyDetailResponse detail(Long id) {
        WarrantyClaim c = repo.findById(id).orElseThrow(() -> ApiException.notFound("Claim not found"));
        OrderItem oi = orderItems.findById(c.getOrderItemId()).orElse(null);
        InventoryItem original = (oi != null) ? inventory.findById(oi.getInventoryId()).orElse(null) : null;
        String buyerEmail = users.findById(c.getBuyerId()).map(u -> u.getEmail()).orElse(null);

        java.util.List<InventoryItem> sameCat = (original != null && original.getAccountCategory() != null)
                ? inventory.findTop50ByStockStatusAndProviderAndAccountCategoryOrderByIdDesc(
                        InventoryItem.Status.AVAILABLE, original.getProvider(), original.getAccountCategory())
                : java.util.List.of();
        boolean sameCategoryStock = !sameCat.isEmpty();
        java.util.List<InventoryItem> pool = sameCategoryStock ? sameCat
                : inventory.findTop50ByStockStatusOrderByIdDesc(InventoryItem.Status.AVAILABLE);

        var candidates = pool.stream()
                .map(store.mailstock.warranty.dto.WarrantyDetailResponse.Candidate::from).toList();
        return new store.mailstock.warranty.dto.WarrantyDetailResponse(
                c, buyerEmail,
                store.mailstock.warranty.dto.WarrantyDetailResponse.PurchasedItem.from(oi),
                store.mailstock.warranty.dto.WarrantyDetailResponse.OriginalAccount.from(original),
                candidates, sameCategoryStock);
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
