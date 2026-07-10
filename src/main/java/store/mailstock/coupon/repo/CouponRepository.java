package store.mailstock.coupon.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import store.mailstock.coupon.entity.Coupon;

import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {
    Optional<Coupon> findByCodeIgnoreCase(String code);

    /**
     * Atomically claim one use of a coupon if it hasn't hit its global cap. Returns the number of
     * rows updated (1 = claimed, 0 = exhausted) so concurrent orders can't exceed {@code maxUses}.
     */
    @Modifying
    @Query("update Coupon c set c.usedCount = c.usedCount + 1 "
            + "where c.id = :id and (c.maxUses is null or c.usedCount < c.maxUses)")
    int tryConsume(@Param("id") Long id);
}
