package store.mailstock.profile.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import store.mailstock.auth.entity.User;
import store.mailstock.auth.repo.UserRepository;
import store.mailstock.common.dto.ApiResponse;
import store.mailstock.common.exception.ApiException;
import store.mailstock.common.util.SecurityUtils;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public record ProfileUpdate(@Size(max = 120) String fullName, @Size(max = 32) String phone) {}
    public record ChangePassword(@NotBlank String currentPassword, @NotBlank @Size(min = 8, max = 100) String newPassword) {}

    @GetMapping("/me")
    public ApiResponse<User> me() { return ApiResponse.ok(SecurityUtils.currentUser()); }

    @PatchMapping("/me")
    public ApiResponse<User> update(@RequestBody ProfileUpdate req) {
        User u = SecurityUtils.currentUser();
        if (req.fullName() != null) u.setFullName(req.fullName());
        if (req.phone() != null) u.setPhone(req.phone());
        return ApiResponse.ok(users.save(u));
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@RequestBody ChangePassword req) {
        User u = SecurityUtils.currentUser();
        if (!encoder.matches(req.currentPassword(), u.getPasswordHash()))
            throw ApiException.badRequest("Current password is incorrect");
        u.setPasswordHash(encoder.encode(req.newPassword()));
        u.setMustChangePassword(false);
        users.save(u);
        return ApiResponse.ok(null);
    }
}
