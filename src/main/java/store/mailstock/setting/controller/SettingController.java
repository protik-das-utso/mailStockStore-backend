package store.mailstock.setting.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

import store.mailstock.common.dto.ApiResponse;
import store.mailstock.setting.entity.Setting;
import store.mailstock.setting.repo.SettingRepository;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
class SettingController {

    private final SettingRepository repo;

    @GetMapping("/public/settings")
    public ApiResponse<List<Setting>> publicList() { return ApiResponse.ok(repo.findAll()); }

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
