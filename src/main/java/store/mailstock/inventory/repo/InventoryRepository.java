package store.mailstock.inventory.repo;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import store.mailstock.inventory.entity.InventoryItem;
import store.mailstock.submission.entity.SellerSubmission;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface InventoryRepository extends JpaRepository<InventoryItem, Long> {
    Page<InventoryItem> findByStockStatusOrderByIdDesc(InventoryItem.Status status, Pageable p);

    /**
     * Lock an inventory row FOR UPDATE so two concurrent buyers can't both purchase the same
     * item: the second buyer blocks until the first commits, then sees it is no longer AVAILABLE.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InventoryItem i where i.id = :id")
    Optional<InventoryItem> findByIdForUpdate(@Param("id") Long id);

    /** Newest available item matching a provider + type — used to auto-pick a warranty replacement. */
    Optional<InventoryItem> findFirstByStockStatusAndProviderAndAccountTypeOrderByIdDesc(
            InventoryItem.Status status, SellerSubmission.Provider provider, SellerSubmission.AccountType accountType);
    Page<InventoryItem> findByStockStatusAndCategoryOrderByIdDesc(InventoryItem.Status status, String category, Pageable p);
    Page<InventoryItem> findByStockStatusAndTitleContainingIgnoreCaseOrderByIdDesc(InventoryItem.Status status, String q, Pageable p);

    /** Admin list: optional status filter + free-text over title/category/country/id, newest first. */
    @org.springframework.data.jpa.repository.Query("select i from InventoryItem i where (:status is null or i.stockStatus = :status) "
            + "and (:q is null or lower(i.title) like lower(concat('%', cast(:q as string), '%')) "
            + "or lower(i.category) like lower(concat('%', cast(:q as string), '%')) "
            + "or lower(i.country) like lower(concat('%', cast(:q as string), '%')) "
            + "or cast(i.id as string) like concat('%', cast(:q as string), '%')) order by i.id desc")
    Page<InventoryItem> adminSearch(@org.springframework.data.repository.query.Param("status") InventoryItem.Status status,
                                    @org.springframework.data.repository.query.Param("q") String q, Pageable p);
    List<InventoryItem> findTop8ByStockStatusOrderByIdDesc(InventoryItem.Status status);
    // Replacement candidates for a warranty: same provider+category first, else any available.
    List<InventoryItem> findTop50ByStockStatusAndProviderAndAccountCategoryOrderByIdDesc(
            InventoryItem.Status status, store.mailstock.submission.entity.SellerSubmission.Provider provider,
            store.mailstock.submission.entity.AccountCategory accountCategory);
    List<InventoryItem> findTop50ByStockStatusOrderByIdDesc(InventoryItem.Status status);
    long countByStockStatus(InventoryItem.Status status);
    long countBySellerIdAndStockStatus(Long sellerId, InventoryItem.Status status);
    long countByStockStatusAndAccountType(InventoryItem.Status status, store.mailstock.submission.entity.SellerSubmission.AccountType accountType);
    long countByStockStatusAndProvider(InventoryItem.Status status, store.mailstock.submission.entity.SellerSubmission.Provider provider);
    long countByStockStatusAndProviderAndAccountType(InventoryItem.Status status, store.mailstock.submission.entity.SellerSubmission.Provider provider, store.mailstock.submission.entity.SellerSubmission.AccountType accountType);
    long countByStockStatusAndProviderAndAccountCategory(InventoryItem.Status status, store.mailstock.submission.entity.SellerSubmission.Provider provider, store.mailstock.submission.entity.AccountCategory accountCategory);

    /** Public browse with optional account-category + provider + free-text (title) filters, newest first. */
    @org.springframework.data.jpa.repository.Query("select i from InventoryItem i where i.stockStatus = :status "
            + "and (:category is null or i.accountCategory = :category) "
            + "and (:provider is null or i.provider = :provider) "
            + "and (:q is null or lower(i.title) like lower(concat('%', cast(:q as string), '%'))) order by i.id desc")
    Page<InventoryItem> browseFiltered(@org.springframework.data.repository.query.Param("status") InventoryItem.Status status,
                                       @org.springframework.data.repository.query.Param("category") store.mailstock.submission.entity.AccountCategory category,
                                       @org.springframework.data.repository.query.Param("provider") store.mailstock.submission.entity.SellerSubmission.Provider provider,
                                       @org.springframework.data.repository.query.Param("q") String q, Pageable p);

    @org.springframework.data.jpa.repository.Query("select coalesce(sum(i.purchasePrice),0) from InventoryItem i")
    BigDecimal sumPurchase();

    @org.springframework.data.jpa.repository.Query("select coalesce(sum(i.sellingPrice - i.purchasePrice),0) from InventoryItem i where i.stockStatus = 'SOLD'")
    BigDecimal sumProfitSold();
}
