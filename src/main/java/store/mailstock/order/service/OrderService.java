package store.mailstock.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import store.mailstock.audit.service.AuditService;
import store.mailstock.common.exception.ApiException;
import store.mailstock.coupon.service.CouponService;
import store.mailstock.inventory.entity.InventoryItem;
import store.mailstock.inventory.service.InventoryService;
import store.mailstock.notification.service.NotificationService;
import store.mailstock.order.dto.ManualDeliverRequest;
import store.mailstock.order.dto.OrderCreateRequest;
import store.mailstock.order.entity.Order;
import store.mailstock.order.entity.OrderItem;
import store.mailstock.order.repo.OrderItemRepository;
import store.mailstock.order.repo.OrderRepository;
import store.mailstock.wallet.service.WalletService;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final InventoryService inventory;
    private final CouponService coupons;
    private final NotificationService notifications;
    private final WalletService wallet;
    private final AuditService audit;

    /**
     * Buying is paid instantly from the buyer's deposited balance: the balance is debited
     * (throws & rolls back if insufficient) and the order is delivered immediately.
     */
    @Transactional
    public Order createOrder(Long buyerId, OrderCreateRequest req) {
        List<InventoryItem> chosen = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        // Lock rows in a stable (id-sorted, de-duplicated) order so concurrent multi-item orders
        // can't deadlock, and so the AVAILABLE check + later markSold are atomic per item.
        List<Long> ids = req.inventoryIds().stream().distinct().sorted().toList();
        for (Long id : ids) {
            InventoryItem i = inventory.getForUpdate(id);
            if (i.getStockStatus() != InventoryItem.Status.AVAILABLE)
                throw ApiException.badRequest("Item unavailable: " + i.getTitle());
            chosen.add(i);
            total = total.add(i.getSellingPrice());
        }

        BigDecimal discount = BigDecimal.ZERO;
        if (req.couponCode() != null && !req.couponCode().isBlank()) {
            discount = coupons.computeDiscount(req.couponCode(), total, buyerId);
        }

        BigDecimal payable = total.subtract(discount).max(BigDecimal.ZERO);

        Order order = Order.builder()
                .buyerId(buyerId).totalAmount(payable)
                .discountAmount(discount).couponCode(req.couponCode())
                .build();
        for (InventoryItem i : chosen) {
            OrderItem oi = OrderItem.builder()
                    .inventoryId(i.getId()).title(i.getTitle()).price(i.getSellingPrice())
                    // Warranty is fixed per category (set on the item at listing time) — buyer doesn't choose it.
                    .warrantyDays(i.getWarrantyDays())
                    .deliveryPayload(i.getDeliveryPayload())   // snapshot credentials at sale time
                    .build();
            oi.setOrder(order);   // owning side — populates order_id on the initial insert
            order.getItems().add(oi);
        }
        order = orders.save(order);

        // Deduct from the buyer's deposited balance; insufficient funds rolls the whole order back.
        wallet.debitPurchase(buyerId, payable, "order:" + order.getId());

        // Deliver immediately.
        Instant now = Instant.now();
        for (OrderItem oi : order.getItems()) {
            // Always stamp an expiry — a 0-day category expires the moment it is sold. Leaving this
            // null would read as "no expiry set" and make the account claimable forever.
            oi.setWarrantyExpiresAt(now.plus(oi.getWarrantyDays() == null ? 0 : oi.getWarrantyDays(), ChronoUnit.DAYS));
            inventory.markSold(oi.getInventoryId());
        }
        order.setStatus(Order.Status.DELIVERED);
        order.setCompletedAt(now);
        order = orders.save(order);

        notifications.notify(buyerId, "ORDER_DELIVERED", "Order delivered",
                "Your order #" + order.getId() + " is ready in your dashboard. "
                        + "Warranty notice: do not change the password or any security options "
                        + "within the first 24 hours after delivery — doing so voids the warranty.");
        notifications.notifyAdmins("NEW_ORDER", "New order",
                "Order #" + order.getId() + " paid from balance: " + payable);

        return order;
    }

    /**
     * Read-only checkout preview for a cart: price the (de-duplicated) items, validate a coupon if
     * given, and return the subtotal / discount / payable. Consumes nothing — safe to call as the
     * buyer edits the cart or types a coupon. An invalid coupon doesn't fail the quote; it comes back
     * with couponApplied=false and a reason so the cart can still check out at full price.
     */
    @Transactional(readOnly = true)
    public store.mailstock.order.dto.OrderQuote quote(Long buyerId, OrderCreateRequest req) {
        List<Long> ids = req.inventoryIds().stream().distinct().toList();
        BigDecimal subtotal = BigDecimal.ZERO;
        int count = 0;
        for (Long id : ids) {
            InventoryItem i = inventory.get(id);
            if (i.getStockStatus() != InventoryItem.Status.AVAILABLE)
                throw ApiException.badRequest("Item unavailable: " + i.getTitle());
            subtotal = subtotal.add(i.getSellingPrice());
            count++;
        }
        BigDecimal discount = BigDecimal.ZERO;
        boolean applied = false;
        String message = null;
        if (req.couponCode() != null && !req.couponCode().isBlank()) {
            try {
                discount = coupons.previewDiscount(req.couponCode(), subtotal, buyerId);
                applied = true;
            } catch (ApiException e) {
                message = e.getMessage();
            }
        }
        BigDecimal payable = subtotal.subtract(discount).max(BigDecimal.ZERO);
        return new store.mailstock.order.dto.OrderQuote(count, subtotal, discount, payable, applied, message);
    }

    @Transactional(readOnly = true)
    public Order get(Long id) {
        return orders.findById(id).orElseThrow(() -> ApiException.notFound("Order not found"));
    }

    @Transactional(readOnly = true)
    public Order getOwned(Long buyerId, Long id) {
        Order o = get(id);
        if (!o.getBuyerId().equals(buyerId)) throw ApiException.forbidden("Not your order");
        return o;
    }

    @Transactional(readOnly = true)
    public Page<Order> mine(Long buyerId, Pageable p) { return orders.findByBuyerIdOrderByIdDesc(buyerId, p); }

    @Transactional(readOnly = true)
    public Page<Order> adminList(Order.Status status, String q, Pageable p) {
        return orders.adminSearch(status, (q != null && !q.isBlank()) ? q.trim() : null, p);
    }

    /** A buyer's vault: every delivered line item they own, with the credentials snapshot. */
    @Transactional(readOnly = true)
    public List<OrderItem> myEmails(Long buyerId) {
        return orderItems.findByBuyerAndStatus(buyerId, Order.Status.DELIVERED);
    }

    /**
     * Admin manual delivery for warranty / replacement: hand an available email to a user as a
     * DELIVERED order. Optionally charge their balance (default off). Either way a wallet
     * transaction + audit log are written as proof.
     */
    @Transactional
    public Order manualDeliver(Long adminId, ManualDeliverRequest req) {
        InventoryItem i = inventory.getForUpdate(req.inventoryId());
        if (i.getStockStatus() != InventoryItem.Status.AVAILABLE)
            throw ApiException.badRequest("Item not available: " + i.getTitle());

        BigDecimal price = i.getSellingPrice();
        BigDecimal charged = req.chargeBalance() ? price : BigDecimal.ZERO;

        Order order = Order.builder()
                .buyerId(req.userId()).totalAmount(charged)
                .discountAmount(BigDecimal.ZERO)
                .build();
        OrderItem oi = OrderItem.builder()
                .inventoryId(i.getId()).title(i.getTitle()).price(price)
                .warrantyDays(i.getWarrantyDays())
                .deliveryPayload(i.getDeliveryPayload())
                .build();
        oi.setOrder(order);
        order.getItems().add(oi);
        order = orders.save(order);

        // Proof transaction in the wallet ledger for both cases.
        if (req.chargeBalance()) {
            wallet.debitPurchase(req.userId(), price, "manual:order:" + order.getId());  // throws if insufficient
        } else {
            wallet.recordProof(req.userId(), "MANUAL_DELIVERY", "manual:free:" + order.getId(), req.note());
        }

        Instant now = Instant.now();
        oi.setWarrantyExpiresAt(now.plus(oi.getWarrantyDays() == null ? 0 : oi.getWarrantyDays(), ChronoUnit.DAYS));
        inventory.markSold(i.getId());
        order.setStatus(Order.Status.DELIVERED);
        order.setCompletedAt(now);
        order = orders.save(order);

        notifications.notify(req.userId(), "MANUAL_DELIVERY", "Email delivered",
                "An email was delivered to your account (order #" + order.getId() + ")."
                        + (req.chargeBalance() ? " " + price + " was charged from your balance." : "")
                        + (req.note() != null && !req.note().isBlank() ? " Note: " + req.note() : "")
                        + " Warranty notice: do not change the password or any security options"
                        + " within the first 24 hours after delivery — doing so voids the warranty.");
        notifications.notifyAdmins("MANUAL_DELIVERY", "Manual delivery",
                "Order #" + order.getId() + " manually delivered to user " + req.userId()
                        + (req.chargeBalance() ? " (charged " + price + ")" : " (free)"));
        audit.log(adminId, "MANUAL_DELIVER", "order", String.valueOf(order.getId()),
                "{\"userId\":" + req.userId() + ",\"inventoryId\":" + req.inventoryId()
                        + ",\"charged\":" + req.chargeBalance() + "}", null);

        return order;
    }
}
