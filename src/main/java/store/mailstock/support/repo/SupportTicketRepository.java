package store.mailstock.support.repo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import store.mailstock.support.entity.SupportTicket;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {
    Page<SupportTicket> findByUserIdOrderByIdDesc(Long userId, Pageable p);
    Page<SupportTicket> findByStatusOrderByIdDesc(SupportTicket.Status status, Pageable p);
    long countByStatus(SupportTicket.Status status);

    /** Admin list: optional status filter + free-text over subject/category/id/user id, newest first. */
    @org.springframework.data.jpa.repository.Query("select t from SupportTicket t where (:status is null or t.status = :status) "
            + "and (:q is null or lower(t.subject) like lower(concat('%', cast(:q as string), '%')) "
            + "or lower(t.category) like lower(concat('%', cast(:q as string), '%')) "
            + "or cast(t.id as string) like concat('%', cast(:q as string), '%') "
            + "or cast(t.userId as string) like concat('%', cast(:q as string), '%')) order by t.id desc")
    Page<SupportTicket> adminSearch(@org.springframework.data.repository.query.Param("status") SupportTicket.Status status,
                                    @org.springframework.data.repository.query.Param("q") String q, Pageable p);
}
