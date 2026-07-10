package store.mailstock.review.controller;

import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import store.mailstock.common.dto.ApiResponse;
import store.mailstock.common.util.SecurityUtils;
import store.mailstock.review.entity.Review;
import store.mailstock.review.repo.ReviewRepository;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
class ReviewController {

    private final ReviewRepository repo;

    public record ReviewCreate(@NotNull Long inventoryId, @Min(1) @Max(5) int rating, @Size(max = 2000) String body) {}

    @GetMapping("/public/reviews/latest")
    public ApiResponse<List<Review>> latest() { return ApiResponse.ok(repo.findTop6ByApprovedTrueOrderByIdDesc()); }

    @GetMapping("/public/reviews/product/{inventoryId}")
    public ApiResponse<List<Review>> forProduct(@PathVariable Long inventoryId) {
        return ApiResponse.ok(repo.findByInventoryIdAndApprovedTrueOrderByIdDesc(inventoryId));
    }

    @PostMapping("/reviews")
    @PreAuthorize("hasRole('BUYER')")
    public ApiResponse<Review> submit(@org.springframework.web.bind.annotation.RequestBody ReviewCreate r) {
        return ApiResponse.ok(repo.save(Review.builder()
                .inventoryId(r.inventoryId()).buyerId(SecurityUtils.currentUserId())
                .rating(r.rating()).body(r.body()).build()));
    }

    @GetMapping("/reviews/admin/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<Review>> pending() { return ApiResponse.ok(repo.findByApprovedFalseOrderByIdDesc()); }

    @PostMapping("/reviews/admin/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Review> approve(@PathVariable Long id, @RequestParam boolean approve) {
        return repo.findById(id).map(rv -> {
            rv.setApproved(approve);
            return ApiResponse.ok(repo.save(rv));
        }).orElse(ApiResponse.fail("Not found"));
    }
}
