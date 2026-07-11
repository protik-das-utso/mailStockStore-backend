package store.mailstock.coupon.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

import store.mailstock.common.exception.ApiException;
import store.mailstock.coupon.entity.Coupon;
import store.mailstock.coupon.entity.CouponRedemption;
import store.mailstock.coupon.repo.CouponRedemptionRepository;
import store.mailstock.coupon.repo.CouponRepository;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository repo;
    private final CouponRedemptionRepository redemptions;

    /**
     * Validate and redeem a coupon for {@code buyerId}, returning the discount. Enforces global
     * cap (atomically) and per-user cap, and records a redemption row. Runs inside the caller's
     * order transaction, so it rolls back with the order if the purchase later fails.
     */
    @Transactional
    public BigDecimal computeDiscount(String code, BigDecimal total, Long buyerId) {
        Coupon c = repo.findByCodeIgnoreCase(code)
                .orElseThrow(() -> ApiException.badRequest("Invalid coupon"));
        if (!c.isActive()) throw ApiException.badRequest("Coupon inactive");
        Instant now = Instant.now();
        if (c.getStartsAt() != null && now.isBefore(c.getStartsAt())) throw ApiException.badRequest("Coupon not started");
        if (c.getExpiresAt() != null && now.isAfter(c.getExpiresAt())) throw ApiException.badRequest("Coupon expired");
        if (c.getMinAmount() != null && total.compareTo(c.getMinAmount()) < 0)
            throw ApiException.badRequest("Order below minimum for this coupon");

        // Per-user cap: reject if this buyer already redeemed it up to the limit.
        if (c.getPerUserLimit() != null
                && redemptions.countByCouponIdAndUserId(c.getId(), buyerId) >= c.getPerUserLimit())
            throw ApiException.badRequest("You have already used this coupon the maximum number of times");

        // Global cap: atomic claim so concurrent orders can't exceed maxUses.
        if (repo.tryConsume(c.getId()) == 0)
            throw ApiException.badRequest("Coupon exhausted");

        redemptions.save(CouponRedemption.builder().couponId(c.getId()).userId(buyerId).build());

        return applyRate(c, total);
    }

    /**
     * Read-only validation + discount preview for a coupon — used by the cart/quote before checkout.
     * Runs the SAME rules as {@link #computeDiscount} (active, window, minimum, per-user & global caps)
     * but does NOT consume a use or record a redemption, so it can be called freely as the buyer types.
     */
    @Transactional(readOnly = true)
    public BigDecimal previewDiscount(String code, BigDecimal total, Long buyerId) {
        Coupon c = repo.findByCodeIgnoreCase(code)
                .orElseThrow(() -> ApiException.badRequest("Invalid coupon"));
        if (!c.isActive()) throw ApiException.badRequest("Coupon inactive");
        Instant now = Instant.now();
        if (c.getStartsAt() != null && now.isBefore(c.getStartsAt())) throw ApiException.badRequest("Coupon not started");
        if (c.getExpiresAt() != null && now.isAfter(c.getExpiresAt())) throw ApiException.badRequest("Coupon expired");
        if (c.getMinAmount() != null && total.compareTo(c.getMinAmount()) < 0)
            throw ApiException.badRequest("Order below minimum for this coupon");
        if (c.getPerUserLimit() != null
                && redemptions.countByCouponIdAndUserId(c.getId(), buyerId) >= c.getPerUserLimit())
            throw ApiException.badRequest("You have already used this coupon the maximum number of times");
        if (c.getMaxUses() != null && c.getUsedCount() != null && c.getUsedCount() >= c.getMaxUses())
            throw ApiException.badRequest("Coupon exhausted");
        return applyRate(c, total);
    }

    private static BigDecimal applyRate(Coupon c, BigDecimal total) {
        BigDecimal discount = c.getDiscountType() == Coupon.DiscountType.PERCENT
                ? total.multiply(c.getDiscountValue()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP)
                : c.getDiscountValue();
        return discount.min(total);
    }
}
