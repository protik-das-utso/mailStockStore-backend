package store.mailstock.setting.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import store.mailstock.common.dto.ApiResponse;
import store.mailstock.setting.entity.Setting;
import store.mailstock.setting.repo.SettingRepository;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
class SettingController {

    private final SettingRepository repo;

    /** Never exposed via the public endpoint even though they share the key/value table with public
     *  config: buyer sell prices (margin data), per-category warranty days, stock targets, and — most
     *  importantly — the abuse auto-flag thresholds (an unauthenticated reader could stay one claim
     *  under the limit forever). {@code price.*} (seller payout) stays public on purpose: the seller
     *  submission form shows it before they submit. */
    private static final List<String> PRIVATE_PREFIXES = List.of("sell.", "warranty.", "stock.target_", "abuse.");
    private static final Set<String> ALWAYS_PUBLIC = Set.of("warranty.policy");

    @GetMapping("/public/settings")
    public ApiResponse<List<Setting>> publicList() {
        return ApiResponse.ok(repo.findAll().stream()
                .filter(s -> ALWAYS_PUBLIC.contains(s.getKey())
                        || PRIVATE_PREFIXES.stream().noneMatch(p -> s.getKey().startsWith(p)))
                .toList());
    }

    @GetMapping("/settings/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<Setting>> all() { return ApiResponse.ok(repo.findAll()); }

    @PutMapping("/settings/admin/{key}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Setting> upsert(@PathVariable String key, @RequestBody Setting s) {
        s.setKey(key); s.setUpdatedAt(Instant.now());
        return ApiResponse.ok(repo.save(s));
    }
}
