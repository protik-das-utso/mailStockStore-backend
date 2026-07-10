package store.mailstock.auth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import store.mailstock.auth.dto.*;
import store.mailstock.auth.service.AuthService;
import store.mailstock.common.dto.ApiResponse;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService auth;

    @PostMapping("/register")
    public ApiResponse<Void> register(@Valid @RequestBody RegisterRequest req) {
        auth.register(req);
        return ApiResponse.ok("Registration successful. Check your email to verify.", null);
    }

    @PostMapping("/verify-email")
    public ApiResponse<Void> verify(@Valid @RequestBody TokenRequest req) {
        auth.verifyEmail(req.token());
        return ApiResponse.ok("Email verified.", null);
    }

    @PostMapping("/resend-verification")
    public ApiResponse<Void> resendVerification(@Valid @RequestBody ResendVerificationRequest req) {
        auth.resendVerification(req.email());
        return ApiResponse.ok("If the account needs verification, an email has been sent.", null);
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return ApiResponse.ok(auth.login(req));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody TokenRequest req) {
        return ApiResponse.ok(auth.refresh(req.token()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody TokenRequest req) {
        auth.logout(req.token());
        return ApiResponse.ok("Logged out.", null);
    }

    @PostMapping("/forgot-password")
    public ApiResponse<Void> forgot(@Valid @RequestBody ForgotPasswordRequest req) {
        auth.forgotPassword(req.email());
        return ApiResponse.ok("If the email exists, a reset link has been sent.", null);
    }

    @PostMapping("/reset-password")
    public ApiResponse<Void> reset(@Valid @RequestBody ResetPasswordRequest req) {
        auth.resetPassword(req.token(), req.newPassword());
        return ApiResponse.ok("Password reset. You may log in.", null);
    }
}
