package store.mailstock.support.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import store.mailstock.support.entity.TicketMessage;

import java.util.List;

public interface TicketMessageRepository extends JpaRepository<TicketMessage, Long> {
    List<TicketMessage> findByTicketIdOrderByIdAsc(Long ticketId);
    long countByTicketId(Long ticketId);
}
