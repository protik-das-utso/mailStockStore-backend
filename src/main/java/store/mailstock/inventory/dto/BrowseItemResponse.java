package store.mailstock.inventory.dto;

import java.math.BigDecimal;

import store.mailstock.common.util.MaskUtil;
import store.mailstock.inventory.entity.InventoryItem;
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
        String country,
        BigDecimal sellingPrice,
        Integer warrantyDays,
        InventoryItem.Status stockStatus
) {
    public static BrowseItemResponse from(InventoryItem i) {
        return new BrowseItemResponse(
                i.getId(),
                MaskUtil.maskEmail(i.getTitle()),
                i.getCategory(),
                i.getDescription(),
                i.getProvider(),
                i.getAccountType(),
                i.getCountry(),
                i.getSellingPrice(),
                i.getWarrantyDays(),
                i.getStockStatus()
        );
    }
}
