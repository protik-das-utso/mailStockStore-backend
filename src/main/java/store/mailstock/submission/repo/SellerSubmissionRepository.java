package store.mailstock.submission.repo;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import store.mailstock.submission.entity.SellerSubmission;

public interface SellerSubmissionRepository extends JpaRepository<SellerSubmission, Long> {
    Page<SellerSubmission> findBySellerIdOrderByIdDesc(Long sellerId, Pageable p);

    /** A seller's prior submissions of the same email address (any status) — powers the duplicate guard. */
    java.util.List<SellerSubmission> findBySellerIdAndEmailAddressIgnoreCase(Long sellerId, String emailAddress);
    Page<SellerSubmission> findByStatusOrderByIdDesc(SellerSubmission.Status status, Pageable p);

    /** Reviewer queue: everything still up for review (PENDING) or currently being checked (CHECKING). */
    List<SellerSubmission> findByStatusInOrderByIdDesc(java.util.Collection<SellerSubmission.Status> statuses);

    /**
     * Atomically claim a submission for review. Succeeds (returns 1) only if the row is still PENDING,
     * or its CHECKING claim has gone stale (claimed before {@code staleBefore}). Two reviewers racing
     * to claim the same id: exactly one UPDATE matches, the other affects 0 rows. This is the real
     * concurrency guard — no reviewer can steal an account another is actively checking.
     */
    @Modifying(clearAutomatically = true)
    @Query("update SellerSubmission s set s.status = :checking, s.claimedBy = :reviewerId, s.claimedAt = :now "
            + "where s.id = :id and (s.status = :pending "
            + "or (s.status = :checking and s.claimedAt < :staleBefore))")
    int claim(@Param("id") Long id, @Param("reviewerId") Long reviewerId,
              @Param("now") Instant now, @Param("staleBefore") Instant staleBefore,
              @Param("pending") SellerSubmission.Status pending,
              @Param("checking") SellerSubmission.Status checking);

    /** Admin search: optional status/provider/type + free-text over email or title. */
    @Query("select s from SellerSubmission s where (:status is null or s.status = :status) "
            + "and (:provider is null or s.provider = :provider) "
            + "and (:type is null or s.accountType = :type) "
            + "and (:q is null or lower(s.emailAddress) like lower(concat('%', cast(:q as string), '%')) "
            + "or lower(s.title) like lower(concat('%', cast(:q as string), '%'))) order by s.id desc")
    Page<SellerSubmission> search(@Param("status") SellerSubmission.Status status,
                                  @Param("provider") SellerSubmission.Provider provider,
                                  @Param("type") SellerSubmission.AccountType type,
                                  @Param("q") String q, Pageable pageable);
    long countBySellerIdAndStatus(Long sellerId, SellerSubmission.Status status);
    long countBySellerId(Long sellerId);
    long countByStatus(SellerSubmission.Status status);
    long countByStatusAndAccountType(SellerSubmission.Status status, SellerSubmission.AccountType accountType);
    long countByStatusAndProviderAndAccountType(SellerSubmission.Status status, SellerSubmission.Provider provider, SellerSubmission.AccountType accountType);
    long countByStatusAndProviderAndAccountCategory(SellerSubmission.Status status, SellerSubmission.Provider provider, store.mailstock.submission.entity.AccountCategory accountCategory);
}
