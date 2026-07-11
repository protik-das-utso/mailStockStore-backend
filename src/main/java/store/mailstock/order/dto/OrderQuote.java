package store.mailstock.order.dto;

import java.math.BigDecimal;

/**
 * Read-only checkout preview for a cart: line-item subtotal, any coupon discount, and the final
 * payable amount. {@code couponApplied} is false (with {@code couponMessage} explaining why) when a
 * supplied coupon code is invalid/expired — the cart still shows the subtotal so checkout can proceed.
 */
public record OrderQuote(
        int itemCount,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal payable,
        boolean couponApplied,
        String couponMessage
) {}
