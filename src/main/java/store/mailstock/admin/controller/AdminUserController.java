package store.mailstock.admin.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import store.mailstock.auth.entity.Role;
import store.mailstock.auth.entity.User;
import store.mailstock.auth.repo.RefreshTokenRepository;
import store.mailstock.auth.repo.UserRepository;
import store.mailstock.common.dto.ApiResponse;
import store.mailstock.common.dto.PageResponse;
import store.mailstock.common.exception.ApiException;
import store.mailstock.common.util.SecurityUtils;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final RefreshTokenRepository refreshTokens;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResponse<User>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) String q) {
        String query = (q != null && !q.isBlank()) ? q.trim() : null;
        return ApiResponse.ok(PageResponse.of(users.search(role, query, PageRequest.of(page, size))));
    }

    @PostMapping("/{id}/lock")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<User> lock(@PathVariable Long id, @RequestParam boolean locked) {
        User u = users.findById(id).orElseThrow(() -> ApiException.notFound("User not found"));
        u.setLocked(locked); return ApiResponse.ok(users.save(u));
    }

    @PostMapping("/{id}/enable")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<User> enable(@PathVariable Long id, @RequestParam boolean enabled) {
        User u = users.findById(id).orElseThrow(() -> ApiException.notFound("User not found"));
        u.setEnabled(enabled); return ApiResponse.ok(users.save(u));
    }

    /** Users the abuse detector has auto-flagged for review, newest first. */
    @GetMapping("/flagged")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<java.util.List<User>> flagged() {
        return ApiResponse.ok(users.findByFlaggedTrueOrderByFlaggedAtDesc());
    }

    /** Manually raise or clear a user's abuse flag (clearing it re-arms auto-flagging for that user). */
    @PostMapping("/{id}/flag")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<User> flag(@PathVariable Long id, @RequestParam boolean flagged,
                                  @RequestParam(required = false) String reason) {
        User u = users.findById(id).orElseThrow(() -> ApiException.notFound("User not found"));
        u.setFlagged(flagged);
        u.setFlaggedReason(flagged ? reason : null);
        u.setFlaggedAt(flagged ? java.time.Instant.now() : null);
        return ApiResponse.ok(users.save(u));
    }

    /** Replace a user's roles (e.g. grant REVIEWER so they can access the reviewer panel). */
    @PutMapping("/{id}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<User> setRoles(@PathVariable Long id, @RequestBody RolesRequest req) {
        if (req == null || req.roles() == null || req.roles().isEmpty())
            throw ApiException.badRequest("A user must have at least one role");
        User u = users.findById(id).orElseThrow(() -> ApiException.notFound("User not found"));
        u.setRoles(new java.util.HashSet<>(req.roles()));
        return ApiResponse.ok(users.save(u));
    }

    /**
     * Admin creates a staff account directly (e.g. a REVIEWER) — no self-registration or email
     * verification needed; the account is active immediately.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<User> create(@RequestBody CreateUserRequest req) {
        if (req == null || req.email() == null || req.email().isBlank()
                || req.password() == null || req.password().length() < 6)
            throw ApiException.badRequest("Email and a password (min 6 chars) are required");
        if (req.roles() == null || req.roles().isEmpty())
            throw ApiException.badRequest("At least one role is required");
        if (users.existsByEmailIgnoreCase(req.email()))
            throw ApiException.conflict("Email already registered");
        User u = User.builder()
                .email(req.email().toLowerCase().trim())
                .passwordHash(encoder.encode(req.password()))
                .fullName(req.fullName())
                .roles(new java.util.HashSet<>(req.roles()))
                .enabled(true).emailVerified(true).locked(false)
                .build();
        return ApiResponse.ok(users.save(u));
    }

    /** Remove a user (e.g. an off-boarded reviewer). Admins cannot delete their own account. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ApiResponse<Void> delete(@PathVariable Long id) {
        User u = users.findById(id).orElseThrow(() -> ApiException.notFound("User not found"));
        if (id.equals(SecurityUtils.currentUserId()))
            throw ApiException.badRequest("You cannot delete your own account");
        refreshTokens.deleteByUserId(id);
        users.delete(u);
        return ApiResponse.ok(null);
    }

    public record RolesRequest(java.util.List<Role> roles) {}
    public record CreateUserRequest(String email, String password, String fullName, java.util.List<Role> roles) {}
}
