package store.mailstock.inventory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import store.mailstock.common.exception.ApiException;
import store.mailstock.inventory.dto.InventoryUpdateRequest;
import store.mailstock.inventory.entity.InventoryItem;
import store.mailstock.inventory.repo.InventoryRepository;
import store.mailstock.submission.entity.SellerSubmission;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository repo;
    private final PricingService pricing;

    @Transactional
    public InventoryItem addFromSubmission(SellerSubmission s, BigDecimal purchase, BigDecimal selling,
                                           String deliveryPayload, String internalNotes) {
        // Warranty is fixed by the account's category (admin-configured), not by what the seller typed.
        int warrantyDays = pricing.warrantyDays(s.getProvider(), s.getAccountCategory());
        return repo.save(InventoryItem.builder()
                .submissionId(s.getId()).sellerId(s.getSellerId())
                .title(s.getTitle()).category(s.getCategory()).description(s.getDescription())
                .provider(s.getProvider()).accountType(s.getAccountType())
                .accountCategory(s.getAccountCategory()).country(s.getCountry())
                .purchasePrice(purchase).sellingPrice(selling)
                .warrantyDays(warrantyDays)
                .deliveryPayload(deliveryPayload).internalNotes(internalNotes)
                .build());
    }

    @Transactional(readOnly = true)
    public InventoryItem get(Long id) {
        return repo.findById(id).orElseThrow(() -> ApiException.notFound("Inventory not found"));
    }

    /** Fetch an item with a pessimistic write lock — use inside a purchase transaction so the
     *  AVAILABLE-check and the transition to SOLD are atomic against other concurrent buyers. */
    @Transactional
    public InventoryItem getForUpdate(Long id) {
        return repo.findByIdForUpdate(id).orElseThrow(() -> ApiException.notFound("Inventory not found"));
    }

    @Transactional(readOnly = true)
    public Page<InventoryItem> browse(store.mailstock.submission.entity.AccountCategory category,
                                      SellerSubmission.Provider provider, String q, Pageable p) {
        return repo.browseFiltered(InventoryItem.Status.AVAILABLE, category, provider,
                (q != null && !q.isBlank()) ? q.trim() : null, p);
    }

    @Transactional(readOnly = true)
    public java.util.List<InventoryItem> featured() {
        return repo.findTop8ByStockStatusOrderByIdDesc(InventoryItem.Status.AVAILABLE);
    }

    @Transactional(readOnly = true)
    public Page<InventoryItem> adminList(InventoryItem.Status status, String q, Pageable p) {
        return repo.adminSearch(status, (q != null && !q.isBlank()) ? q.trim() : null, p);
    }

    @Transactional
    public InventoryItem update(Long id, InventoryUpdateRequest req) {
        InventoryItem i = get(id);
        if (req.title() != null) i.setTitle(req.title());
        if (req.category() != null) i.setCategory(req.category());
        if (req.description() != null) i.setDescription(req.description());
        if (req.sellingPrice() != null) i.setSellingPrice(req.sellingPrice());
        if (req.warrantyDays() != null) i.setWarrantyDays(req.warrantyDays());
        if (req.deliveryPayload() != null) i.setDeliveryPayload(req.deliveryPayload());
        if (req.internalNotes() != null) i.setInternalNotes(req.internalNotes());
        if (req.stockStatus() != null) i.setStockStatus(InventoryItem.Status.valueOf(req.stockStatus()));
        return repo.save(i);
    }

    @Transactional
    public InventoryItem markSold(Long id) {
        InventoryItem i = get(id);
        i.setStockStatus(InventoryItem.Status.SOLD);
        return repo.save(i);
    }
}
