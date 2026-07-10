package store.mailstock.order.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import store.mailstock.order.entity.Order;
import store.mailstock.order.entity.OrderItem;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByOrderId(Long orderId);

    /** Line items a buyer owns in a given order status — their purchased-email vault when DELIVERED. */
    @Query("select oi from OrderItem oi join Order o on oi.orderId = o.id "
            + "where o.buyerId = :buyerId and o.status = :status order by oi.id desc")
    List<OrderItem> findByBuyerAndStatus(@Param("buyerId") Long buyerId, @Param("status") Order.Status status);
}
