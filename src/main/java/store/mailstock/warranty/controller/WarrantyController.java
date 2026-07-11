package store.mailstock.warranty.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import store.mailstock.common.dto.ApiResponse;
import store.mailstock.common.dto.PageResponse;
import store.mailstock.common.util.SecurityUtils;
import store.mailstock.warranty.dto.WarrantyCreateRequest;
import store.mailstock.warranty.dto.WarrantyDecisionRequest;
import store.mailstock.warranty.entity.WarrantyClaim;
import store.mailstock.warranty.service.WarrantyService;

@RestController
@RequestMapping("/api/warranty")
@RequiredArgsConstructor
public class WarrantyController {

    private final WarrantyService svc;

    @PostMapping
    @PreAuthorize("hasRole('BUYER')")
    public ApiResponse<WarrantyClaim> open(@Valid @RequestBody WarrantyCreateRequest req) {
        return ApiResponse.ok(svc.open(SecurityUtils.currentUserId(), req));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('BUYER')")
    public ApiResponse<PageResponse<WarrantyClaim>> mine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.of(svc.mine(SecurityUtils.currentUserId(), PageRequest.of(page, size))));
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResponse<WarrantyClaim>> adminList(
            @RequestParam(required = false) WarrantyClaim.Status status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.of(svc.adminList(status, q, PageRequest.of(page, size))));
    }

    @GetMapping("/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<store.mailstock.warranty.dto.WarrantyDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.ok(svc.detail(id));
    }

    @PostMapping("/admin/{id}/decision")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<WarrantyClaim> decide(@PathVariable Long id, @Valid @RequestBody WarrantyDecisionRequest req) {
        return ApiResponse.ok(svc.decide(SecurityUtils.currentUserId(), id, req));
    }
}
