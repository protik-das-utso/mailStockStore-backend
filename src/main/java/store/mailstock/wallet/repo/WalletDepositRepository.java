package store.mailstock.wallet.repo;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import store.mailstock.wallet.entity.WalletDeposit;

import java.util.List;
import java.util.Optional;

public interface WalletDepositRepository extends JpaRepository<WalletDeposit, Long> {
    Page<WalletDeposit> findByUserIdOrderByIdDesc(Long userId, Pageable p);

    /** Lock a deposit row FOR UPDATE so validateNow + the reconciler can't double-credit it. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from WalletDeposit d where d.id = :id")
    Optional<WalletDeposit> findByIdForUpdate(@Param("id") Long id);
    Page<WalletDeposit> findByStatusOrderByIdDesc(WalletDeposit.Status status, Pageable p);
    long countByStatus(WalletDeposit.Status status);

    /** How many of a user's deposits ended in a given status — REJECTED feeds the abuse auto-flag threshold. */
    long countByUserIdAndStatus(Long userId, WalletDeposit.Status status);

    /** All deposits in a status — the reconciler scans the (small) PENDING set each poll. */
    List<WalletDeposit> findByStatus(WalletDeposit.Status status);

    /** True if this Binance Pay transaction id has already credited some deposit (double-credit guard). */
    boolean existsByTxidAndStatus(String txid, WalletDeposit.Status status);

    /** True if this transaction/order id was ever submitted by anyone, in ANY status
     *  (PENDING / APPROVED / REJECTED) — used to reject re-use of a txid outright. */
    boolean existsByTxidIgnoreCase(String txid);

    /** Admin list: optional status filter + free-text over txid/method/note/id/user id, newest first. */
    @org.springframework.data.jpa.repository.Query("select d from WalletDeposit d where (:status is null or d.status = :status) "
            + "and (:q is null or lower(d.txid) like lower(concat('%', cast(:q as string), '%')) "
            + "or lower(d.method) like lower(concat('%', cast(:q as string), '%')) "
            + "or lower(d.adminNote) like lower(concat('%', cast(:q as string), '%')) "
            + "or cast(d.id as string) like concat('%', cast(:q as string), '%') "
            + "or cast(d.userId as string) like concat('%', cast(:q as string), '%')) order by d.id desc")
    Page<WalletDeposit> adminSearch(@org.springframework.data.repository.query.Param("status") WalletDeposit.Status status,
                                    @org.springframework.data.repository.query.Param("q") String q, Pageable p);
}
