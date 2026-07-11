package store.mailstock.inventory.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import store.mailstock.common.dto.ApiResponse;
import store.mailstock.common.dto.PageResponse;
import store.mailstock.inventory.dto.BrowseItemResponse;
import store.mailstock.inventory.dto.InventoryUpdateRequest;
import store.mailstock.inventory.entity.InventoryItem;
import store.mailstock.inventory.service.InventoryService;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService svc;

    // Public browse — masked email, no delivery payload / internal notes ever leave the server.
    @GetMapping("/browse")
    public ApiResponse<PageResponse<BrowseItemResponse>> browse(
            @RequestParam(required = false) store.mailstock.submission.entity.AccountCategory accountCategory,
            @RequestParam(required = false) store.mailstock.submission.entity.SellerSubmission.Provider provider,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.of(
                svc.browse(accountCategory, provider, q, PageRequest.of(page, size)).map(BrowseItemResponse::from)));
    }

    @GetMapping("/browse/featured")
    public ApiResponse<List<BrowseItemResponse>> featured() {
        return ApiResponse.ok(svc.featured().stream().map(BrowseItemResponse::from).toList());
    }

    @GetMapping("/browse/{id}")
    public ApiResponse<BrowseItemResponse> productPage(@PathVariable Long id) {
        return ApiResponse.ok(BrowseItemResponse.from(svc.get(id)));
    }

    // Admin
    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResponse<InventoryItem>> adminList(
            @RequestParam(required = false) InventoryItem.Status status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageResponse.of(svc.adminList(status, q, PageRequest.of(page, size))));
    }

    @PatchMapping("/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<InventoryItem> update(@PathVariable Long id, @Valid @RequestBody InventoryUpdateRequest req) {
        return ApiResponse.ok(svc.update(id, req));
    }
}
