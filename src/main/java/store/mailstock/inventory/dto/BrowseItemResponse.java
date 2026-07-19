package store.mailstock.inventory.dto;

import java.math.BigDecimal;

import store.mailstock.common.util.MaskUtil;
import store.mailstock.inventory.entity.InventoryItem;
import store.mailstock.submission.entity.AccountCategory;
import store.mailstock.submission.entity.SellerSubmission;

/**
 * Public, safe view of an inventory item. Masks the email (title) and omits
 * delivery credentials and internal notes so they can never reach unauthenticated clients.
 */
public record BrowseItemResponse(
        Long id,
        String title,
        String category,
        String description,
        SellerSubmission.Provider provider,
        SellerSubmission.AccountType accountType,
        AccountCategory accountCategory,
        String accountCategoryLabel,
        String country,
        Integer accountCreationYear,
        BigDecimal sellingPrice,
        Integer warrantyDays,
        InventoryItem.Status stockStatus
) {
    /** Resolve the selling price: use the item's explicit override, else resolve from pricing service. */
    public static BrowseItemResponse from(InventoryItem i, store.mailstock.inventory.service.PricingService pricing) {
        BigDecimal price = i.getSellingPrice() != null ? i.getSellingPrice()
                : pricing.sellPrice(i.getProvider(), i.getAccountCategory());
        return new BrowseItemResponse(
                i.getId(),
                MaskUtil.maskEmail(i.getTitle()),
                i.getCategory(),
                i.getDescription(),
                i.getProvider(),
                i.getAccountType(),
                i.getAccountCategory(),
                i.getAccountCategory() != null ? i.getAccountCategory().label : null,
                i.getCountry(),
                i.getAccountCreationYear(),
                price,
                i.getWarrantyDays(),
                i.getStockStatus()
        );
    }
}
