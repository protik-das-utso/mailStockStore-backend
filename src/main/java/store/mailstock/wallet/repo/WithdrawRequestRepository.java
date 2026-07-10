package store.mailstock.wallet.repo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import store.mailstock.wallet.entity.WithdrawRequest;

public interface WithdrawRequestRepository extends JpaRepository<WithdrawRequest, Long> {
    Page<WithdrawRequest> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);
    Page<WithdrawRequest> findByStatusOrderByIdDesc(WithdrawRequest.Status status, Pageable pageable);
    long countByStatus(WithdrawRequest.Status status);

    /** Admin list: optional status filter + free-text over destination/method/payout txid/note/id/user id, newest first. */
    @org.springframework.data.jpa.repository.Query("select w from WithdrawRequest w where (:status is null or w.status = :status) "
            + "and (:q is null or lower(w.destination) like lower(concat('%', cast(:q as string), '%')) "
            + "or lower(w.method) like lower(concat('%', cast(:q as string), '%')) "
            + "or lower(w.payoutTxid) like lower(concat('%', cast(:q as string), '%')) "
            + "or lower(w.adminNote) like lower(concat('%', cast(:q as string), '%')) "
            + "or cast(w.id as string) like concat('%', cast(:q as string), '%') "
            + "or cast(w.userId as string) like concat('%', cast(:q as string), '%')) order by w.id desc")
    Page<WithdrawRequest> adminSearch(@org.springframework.data.repository.query.Param("status") WithdrawRequest.Status status,
                                      @org.springframework.data.repository.query.Param("q") String q, Pageable pageable);
}
