package store.mailstock.warranty.repo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import store.mailstock.warranty.entity.WarrantyClaim;

public interface WarrantyClaimRepository extends JpaRepository<WarrantyClaim, Long> {
    Page<WarrantyClaim> findByBuyerIdOrderByIdDesc(Long buyerId, Pageable p);
    Page<WarrantyClaim> findByStatusOrderByIdDesc(WarrantyClaim.Status status, Pageable p);

    /** Admin list: optional status filter + free-text over reason/description/note/id/buyer id, newest first. */
    @org.springframework.data.jpa.repository.Query("select c from WarrantyClaim c where (:status is null or c.status = :status) "
            + "and (:q is null or lower(c.reason) like lower(concat('%', cast(:q as string), '%')) "
            + "or lower(c.description) like lower(concat('%', cast(:q as string), '%')) "
            + "or lower(c.adminNote) like lower(concat('%', cast(:q as string), '%')) "
            + "or cast(c.id as string) like concat('%', cast(:q as string), '%') "
            + "or cast(c.buyerId as string) like concat('%', cast(:q as string), '%')) order by c.id desc")
    Page<WarrantyClaim> adminSearch(@org.springframework.data.repository.query.Param("status") WarrantyClaim.Status status,
                                    @org.springframework.data.repository.query.Param("q") String q, Pageable p);
    long countByStatus(WarrantyClaim.Status status);
    long countByBuyerIdAndStatus(Long buyerId, WarrantyClaim.Status status);
}
