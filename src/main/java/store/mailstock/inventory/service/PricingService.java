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
