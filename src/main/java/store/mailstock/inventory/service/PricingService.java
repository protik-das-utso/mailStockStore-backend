package store.mailstock.inventory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import store.mailstock.setting.entity.Setting;
import store.mailstock.setting.repo.SettingRepository;
import store.mailstock.submission.entity.AccountCategory;
import store.mailstock.submission.entity.SellerSubmission;

/**
 * Central lookup for the admin-configured warranty length of an account. Warranty is fixed per
 * provider × category (buyers don't choose it): the value lives in settings as
 * {@code warranty.<provider>_<category>} (e.g. warranty.gmail_new_0d_no_2fa). When unset it falls
 * back to the category's built-in {@link AccountCategory#defaultWarrantyDays default}.
 */
@Service
@RequiredArgsConstructor
public class PricingService {

    private final SettingRepository settings;

    /** Admin-configured default buyer price for a provider × category (sell.<provider>_<category>), or null if unset. */
    public java.math.BigDecimal sellPrice(SellerSubmission.Provider provider, AccountCategory category) {
        return priceFor("sell", provider, category);
    }

    /** Admin-configured payout to seller for a provider × category (price.<provider>_<category>), or null if unset. */
    public java.math.BigDecimal payoutPrice(SellerSubmission.Provider provider, AccountCategory category) {
        return priceFor("price", provider, category);
    }

    private java.math.BigDecimal priceFor(String prefix, SellerSubmission.Provider provider, AccountCategory category) {
        if (category == null) return null;
        String key = prefix + "." + (provider == null ? SellerSubmission.Provider.GMAIL : provider).name().toLowerCase()
                + "_" + category.key();
        return settings.findById(key)
                .map(Setting::getValue)
                .map(v -> { try { return new java.math.BigDecimal(v.trim()); } catch (RuntimeException e) { return null; } })
                .orElse(null);
    }

    /** A provider × category is on sale to buyers only when a positive sell price is configured. */
    public boolean isSellable(SellerSubmission.Provider provider, AccountCategory category) {
        java.math.BigDecimal p = sellPrice(provider, category);
        return p != null && p.signum() > 0;
    }

    /**
     * The set of "&lt;provider&gt;_&lt;category&gt;" keys (e.g. "gmail_new_0d_no_2fa") that currently have a
     * positive sell price. Used to hide unpriced categories from the buyer browse query. Empty set
     * means nothing is on sale — the caller must handle that (an empty IN (...) matches no rows).
     */
    public java.util.Set<String> sellableKeys() {
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (SellerSubmission.Provider p : SellerSubmission.Provider.values())
            for (AccountCategory c : AccountCategory.values())
                if (isSellable(p, c)) keys.add(p.name().toLowerCase() + "_" + c.key());
        return keys;
    }

    /** Sellers may only submit a provider × category when a positive payout price is configured. */
    public boolean isBuyable(SellerSubmission.Provider provider, AccountCategory category) {
        java.math.BigDecimal p = payoutPrice(provider, category);
        return p != null && p.signum() > 0;
    }

    /** Warranty days granted for a provider × category (admin override → category default). */
    public int warrantyDays(SellerSubmission.Provider provider, AccountCategory category) {
        if (category == null) return 7;
        String key = "warranty." + (provider == null ? SellerSubmission.Provider.GMAIL : provider).name().toLowerCase()
                + "_" + category.key();
        return settings.findById(key)
                .map(Setting::getValue)
                .map(v -> { try { return Integer.parseInt(v.trim()); } catch (RuntimeException e) { return category.defaultWarrantyDays; } })
                .orElse(category.defaultWarrantyDays);
    }
}
