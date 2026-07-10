package store.mailstock.coupon.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import store.mailstock.common.dto.ApiResponse;
import store.mailstock.coupon.entity.Coupon;
import store.mailstock.coupon.repo.CouponRepository;

import java.util.List;

@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponRepository repo;

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<Coupon>> list() { return ApiResponse.ok(repo.findAll()); }

    @PostMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Coupon> create(@RequestBody Coupon c) {
        c.setId(null);
        return ApiResponse.ok(repo.save(c));
    }

    @PutMapping("/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Coupon> update(@PathVariable Long id, @RequestBody Coupon c) {
        c.setId(id);
        return ApiResponse.ok(repo.save(c));
    }

    @DeleteMapping("/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) { repo.deleteById(id); return ApiResponse.ok(null); }
}
