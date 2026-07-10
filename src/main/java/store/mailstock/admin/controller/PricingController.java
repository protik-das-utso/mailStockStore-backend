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
import store.mailstock.submission.entity.SellerSubmission;
import store.mailstock.submission.repo.SellerSubmissionRepository;

/**
 * Pricing & stock panel: admin sets the payout/sell price for each Gmail type and
 * sees live availability (in stock) versus the target quantity that is needed.
 */
@RestController
@RequestMapping("/api/admin/pricing")
@RequiredArgsConstructor
public class PricingController {

    private final SettingRepository settings;
    private final InventoryRepository inventory;
    private final SellerSubmissionRepository submissions;

    private record TypeConfig(String key, String label, SellerSubmission.Provider provider, SellerSubmission.AccountType type) {}

    // One row per provider × account type. key = "<provider>_<type>" e.g. gmail_old, outlook_new.
    private static final List<TypeConfig> TYPES = List.of(
            new TypeConfig("gmail_old", "Gmail — Old (aged)", SellerSubmission.Provider.GMAIL, SellerSubmission.AccountType.OLD),
            new TypeConfig("gmail_new", "Gmail — New (fresh)", SellerSubmission.Provider.GMAIL, SellerSubmission.AccountType.NEW),
            new TypeConfig("outlook_old", "Outlook — Old (aged)", SellerSubmission.Provider.OUTLOOK, SellerSubmission.AccountType.OLD),
            new TypeConfig("outlook_new", "Outlook — New (fresh)", SellerSubmission.Provider.OUTLOOK, SellerSubmission.AccountType.NEW)
    );

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<Map<String, Object>>> list() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (TypeConfig t : TYPES) {
            long available = inventory.countByStockStatusAndProviderAndAccountType(InventoryItem.Status.AVAILABLE, t.provider(), t.type());
            long reserved = inventory.countByStockStatusAndProviderAndAccountType(InventoryItem.Status.RESERVED, t.provider(), t.type());
            long sold = inventory.countByStockStatusAndProviderAndAccountType(InventoryItem.Status.SOLD, t.provider(), t.type());
            long pendingSubs = submissions.countByStatusAndProviderAndAccountType(SellerSubmission.Status.PENDING, t.provider(), t.type());
            long target = num("stock.target_" + t.key(), 10).longValue();

            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("key", t.key());
            row.put("label", t.label());
            row.put("provider", t.provider().name());
            row.put("payoutPrice", num("price." + t.key(), 0));
            row.put("sellPrice", num("sell." + t.key(), 0));
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

    public record PricingUpdate(String key, BigDecimal payoutPrice, BigDecimal sellPrice, Long targetStock) {}

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<String> update(@RequestBody List<PricingUpdate> updates) {
        for (PricingUpdate u : updates) {
            if (u.payoutPrice() != null) put("price." + u.key(), u.payoutPrice().toPlainString());
            if (u.sellPrice() != null) put("sell." + u.key(), u.sellPrice().toPlainString());
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
