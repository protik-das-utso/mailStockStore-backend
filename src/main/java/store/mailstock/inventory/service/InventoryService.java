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
        // sellingPrice can be NULL — it will resolve dynamically from sell.<provider>_<category> at browse time
        return repo.save(InventoryItem.builder()
                .submissionId(s.getId()).sellerId(s.getSellerId())
                .title(s.getTitle()).category(s.getCategory()).description(s.getDescription())
                .provider(s.getProvider()).accountType(s.getAccountType())
                .accountCategory(s.getAccountCategory()).country(s.getCountry())
                .accountCreationYear(s.getAccountCreationYear())
                .purchasePrice(purchase).sellingPrice(selling)
                .warrantyDays(warrantyDays)
                .deliveryPayload(deliveryPayload).internalNotes(internalNotes)
                .build());
    }

    /**
     * Admin adds an account they already own straight into inventory — no seller submission,
     * no payout. The item is created AVAILABLE, i.e. immediately on sale to buyers. Mirrors the
     * submission path: category-driven 2FA requirement, warranty and default sell price come
     * from the same admin pricing settings.
     */
    @Transactional
    public InventoryItem adminCreate(store.mailstock.inventory.dto.InventoryCreateRequest req) {
        var provider = req.provider() == null ? SellerSubmission.Provider.GMAIL : req.provider();
        var category = req.accountCategory();
        if (category.requires2FA && (req.twoFactorCode() == null || req.twoFactorCode().isBlank()))
            throw ApiException.badRequest("A 2FA/TOTP secret is required for \"" + category.label + "\" accounts");

        String email = req.emailAddress().trim();
        // Same email can't be on sale twice — sold/dead/archived copies don't block re-adding.
        if (repo.existsByTitleIgnoreCaseAndStockStatusIn(email,
                java.util.List.of(InventoryItem.Status.AVAILABLE, InventoryItem.Status.RESERVED)))
            throw ApiException.conflict("An item for " + email + " is already on sale");

        // Selling price: NULL means follow sell.<provider>_<category>, explicit value locks it
        BigDecimal selling = req.sellingPrice();
        if (selling == null) {
            selling = pricing.sellPrice(provider, category);
            if (selling == null || selling.signum() <= 0)
                throw ApiException.badRequest("No selling price given and no default price is configured for "
                        + provider + " / " + category.label);
        }

        StringBuilder creds = new StringBuilder();
        creds.append("Provider: ").append(provider).append('\n');
        creds.append("Email: ").append(email).append('\n');
        creds.append("Password: ").append(req.emailPassword()).append('\n');
        if (req.twoFactorCode() != null && !req.twoFactorCode().isBlank())
            creds.append("2FA secret: ").append(req.twoFactorCode()).append('\n');
        if (req.backupCodes() != null && !req.backupCodes().isBlank())
            creds.append("Backup codes: ").append(req.backupCodes()).append('\n');
        if (req.recoveryEmail() != null && !req.recoveryEmail().isBlank())
            creds.append("Recovery email: ").append(req.recoveryEmail()).append('\n');
        if (req.country() != null && !req.country().isBlank())
            creds.append("Country: ").append(req.country()).append('\n');
        creds.append("Category: ").append(category.label);

        return repo.save(InventoryItem.builder()
                .title((req.title() != null && !req.title().isBlank()) ? req.title().trim() : email)
                .category(provider == SellerSubmission.Provider.OUTLOOK ? "Outlook" : "Gmail")
                .description(req.description())
                .provider(provider).accountType(category.legacyType)
                .accountCategory(category).country(req.country())
                .accountCreationYear(req.accountCreationYear())
                .purchasePrice(req.purchasePrice() == null ? BigDecimal.ZERO : req.purchasePrice())
                .sellingPrice(selling)  // can be NULL — will resolve dynamically at browse time
                .warrantyDays(pricing.warrantyDays(provider, category))
                .deliveryPayload(creds.toString())
                .internalNotes(req.internalNotes())
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
        // Only categories with a positive sell price are on sale; unpriced ones are hidden from buyers.
        java.util.Set<String> sellable = pricing.sellableKeys();
        if (sellable.isEmpty()) return Page.empty(p);
        return repo.browseFiltered(InventoryItem.Status.AVAILABLE, category, provider,
                (q != null && !q.isBlank()) ? q.trim() : null, sellable, p);
    }

    @Transactional(readOnly = true)
    public java.util.List<InventoryItem> featured() {
        java.util.Set<String> sellable = pricing.sellableKeys();
        if (sellable.isEmpty()) return java.util.List.of();
        return repo.findTop8Sellable(InventoryItem.Status.AVAILABLE, sellable, Pageable.ofSize(8));
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
        // Price: "use category price" clears the override (item follows Price & Stocks again);
        // an explicit sellingPrice locks the item to that fixed price.
        if (Boolean.TRUE.equals(req.useCategoryPrice())) i.setSellingPrice(null);
        else if (req.sellingPrice() != null) i.setSellingPrice(req.sellingPrice());
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
