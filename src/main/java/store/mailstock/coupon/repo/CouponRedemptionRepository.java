package store.mailstock.coupon.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import store.mailstock.coupon.entity.CouponRedemption;

public interface CouponRedemptionRepository extends JpaRepository<CouponRedemption, Long> {
    long countByCouponIdAndUserId(Long couponId, Long userId);
}
