package store.mailstock.admin.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import store.mailstock.common.dto.ApiResponse;
import store.mailstock.inventory.entity.InventoryItem;
import store.mailstock.inventory.repo.InventoryRepository;
import store.mailstock.setting.entity.Setting;
import store.mailstock.setting.repo.SettingRepository;
import store.mailstock.submission.entity.AccountCategory;
import store.mailstock.submission.entity.SellerSubmission;
import store.mailstock.submission.repo.SellerSubmissionRepository;

/**
 * Pricing & stock panel: admin sets the payout/sell price for every provider × account category
 * and sees live availability (in stock) versus the target quantity that is needed. Rows carry a
 * {@code provider} so the frontend can group them (Gmail / Outlook) for an understandable panel.
 */
@RestController
@RequestMapping("/api/admin/pricing")
@RequiredArgsConstructor
public class PricingController {

    private final SettingRepository settings;
    private final InventoryRepository inventory;
    private final SellerSubmissionRepository submissions;

    private record TypeConfig(String key, String label, SellerSubmission.Provider provider, AccountCategory category) {}

    // One row per provider × account category. key = "<provider>_<category>" e.g. gmail_new_1_3m_no_2fa, outlook_y3_plus.
    private static final List<TypeConfig> TYPES = buildTypes();

    private static List<TypeConfig> buildTypes() {
        List<TypeConfig> rows = new ArrayList<>();
        for (SellerSubmission.Provider p : SellerSubmission.Provider.values()) {
            String prov = p.name().charAt(0) + p.name().substring(1).toLowerCase(); // Gmail / Outlook
            for (AccountCategory c : AccountCategory.values())
                rows.add(new TypeConfig(p.name().toLowerCase() + "_" + c.key(), prov + " — " + c.label, p, c));
        }
        return List.copyOf(rows);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<Map<String, Object>>> list() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (TypeConfig t : TYPES) {
            long available = inventory.countByStockStatusAndProviderAndAccountCategory(InventoryItem.Status.AVAILABLE, t.provider(), t.category());
            long reserved = inventory.countByStockStatusAndProviderAndAccountCategory(InventoryItem.Status.RESERVED, t.provider(), t.category());
            long sold = inventory.countByStockStatusAndProviderAndAccountCategory(InventoryItem.Status.SOLD, t.provider(), t.category());
            long pendingSubs = submissions.countByStatusAndProviderAndAccountCategory(SellerSubmission.Status.PENDING, t.provider(), t.category());
            long target = num("stock.target_" + t.key(), 10).longValue();

            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("key", t.key());
            row.put("label", t.label());
            row.put("provider", t.provider().name());
            row.put("categoryLabel", t.category().label);
            row.put("payoutPrice", num("price." + t.key(), 0));
            row.put("sellPrice", num("sell." + t.key(), 0));
            // Warranty is fixed per category (buyer doesn't choose) — admin-editable, defaults to the category policy.
            row.put("warrantyDays", num("warranty." + t.key(), t.category().defaultWarrantyDays).longValue());
            row.put("targetStock", target);
            row.put("available", available);
            row.put("reserved", reserved);
            row.put("sold", sold);
            row.put("pendingSubmissions", pendingSubs);
            row.put("shortfall", Math.max(0, target - available)); // how many more are needed
            rows.add(row);
        }
        return ApiResponse.ok(rows);
    }

    public record PricingUpdate(String key, BigDecimal payoutPrice, BigDecimal sellPrice,
                                Integer warrantyDays, Long targetStock) {}

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<String> update(@RequestBody List<PricingUpdate> updates) {
        for (PricingUpdate u : updates) {
            if (u.payoutPrice() != null) put("price." + u.key(), u.payoutPrice().toPlainString());
            if (u.sellPrice() != null) put("sell." + u.key(), u.sellPrice().toPlainString());
            if (u.warrantyDays() != null) put("warranty." + u.key(), String.valueOf(Math.max(0, u.warrantyDays())));
            if (u.targetStock() != null) put("stock.target_" + u.key(), String.valueOf(u.targetStock()));
        }
        return ApiResponse.ok("saved");
    }

    private BigDecimal num(String key, double fallback) {
        return settings.findById(key)
                .map(s -> {
                    try { return new BigDecimal(s.getValue()); }
                    catch (RuntimeException e) { return BigDecimal.valueOf(fallback); }
                })
                .orElse(BigDecimal.valueOf(fallback));
    }

    private void put(String key, String value) {
        settings.save(Setting.builder().key(key).value(value).updatedAt(Instant.now()).build());
    }
}
