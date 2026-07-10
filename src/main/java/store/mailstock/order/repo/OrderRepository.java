package store.mailstock.order.repo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import store.mailstock.order.entity.Order;

import java.math.BigDecimal;
import java.time.Instant;

public interface OrderRepository extends JpaRepository<Order, Long> {
    Page<Order> findByBuyerIdOrderByIdDesc(Long buyerId, Pageable p);
    Page<Order> findByStatusOrderByIdDesc(Order.Status status, Pageable p);

    /** Admin list: optional status filter + free-text over coupon code / order id / buyer id, newest first. */
    @org.springframework.data.jpa.repository.Query("select o from Order o where (:status is null or o.status = :status) "
            + "and (:q is null or lower(o.couponCode) like lower(concat('%', cast(:q as string), '%')) "
            + "or cast(o.id as string) like concat('%', cast(:q as string), '%') "
            + "or cast(o.buyerId as string) like concat('%', cast(:q as string), '%')) order by o.id desc")
    Page<Order> adminSearch(@org.springframework.data.repository.query.Param("status") Order.Status status,
                            @org.springframework.data.repository.query.Param("q") String q, Pageable p);
    long countByBuyerIdAndStatus(Long buyerId, Order.Status status);
    long countByStatus(Order.Status status);

    @org.springframework.data.jpa.repository.Query("select coalesce(sum(o.totalAmount),0) from Order o where o.status in ('PAID','DELIVERED') and o.completedAt >= :from")
    BigDecimal revenueSince(Instant from);
}
