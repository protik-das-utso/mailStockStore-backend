package store.mailstock.warranty.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import store.mailstock.common.util.MaskUtil;
import store.mailstock.inventory.entity.InventoryItem;
import store.mailstock.order.entity.OrderItem;
import store.mailstock.submission.entity.AccountCategory;
import store.mailstock.submission.entity.SellerSubmission;
import store.mailstock.warranty.entity.WarrantyClaim;

/** Everything the admin needs to action a warranty claim from one screen. */
public record WarrantyDetailResponse(
        WarrantyClaim claim,
        String buyerEmail,
        PurchasedItem orderItem,
        OriginalAccount original,
        List<Candidate> candidates,       // available stock the admin can deliver as a replacement
        boolean sameCategoryStock         // whether any candidate matches the original provider+category
) {
    public record PurchasedItem(Long id, String title, BigDecimal price, Integer warrantyDays,
                                Instant warrantyExpiresAt, String deliveryPayload) {
        public static PurchasedItem from(OrderItem oi) {
            return oi == null ? null : new PurchasedItem(oi.getId(), oi.getTitle(), oi.getPrice(),
                    oi.getWarrantyDays(), oi.getWarrantyExpiresAt(), oi.getDeliveryPayload());
        }
    }

    public record OriginalAccount(Long inventoryId, String title, SellerSubmission.Provider provider,
                                  AccountCategory accountCategory, String accountCategoryLabel,
                                  String country, Long sellerId) {
        public static OriginalAccount from(InventoryItem i) {
            return i == null ? null : new OriginalAccount(i.getId(), i.getTitle(), i.getProvider(),
                    i.getAccountCategory(), i.getAccountCategory() != null ? i.getAccountCategory().label : null,
                    i.getCountry(), i.getSellerId());
        }
    }

    public record Candidate(Long id, String maskedTitle, SellerSubmission.Provider provider,
                            AccountCategory accountCategory, String accountCategoryLabel,
                            String country, BigDecimal sellingPrice) {
        public static Candidate from(InventoryItem i, store.mailstock.inventory.service.PricingService pricing) {
            BigDecimal price = i.getSellingPrice() != null ? i.getSellingPrice()
                    : pricing.sellPrice(i.getProvider(), i.getAccountCategory());
            return new Candidate(i.getId(), MaskUtil.maskEmail(i.getTitle()), i.getProvider(),
                    i.getAccountCategory(), i.getAccountCategory() != null ? i.getAccountCategory().label : null,
                    i.getCountry(), price);
        }
    }
}
