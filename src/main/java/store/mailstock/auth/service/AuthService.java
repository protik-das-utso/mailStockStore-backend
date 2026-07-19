package store.mailstock.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import store.mailstock.auth.dto.*;
import store.mailstock.auth.entity.*;
import store.mailstock.auth.repo.*;
import store.mailstock.common.exception.ApiException;
import store.mailstock.config.JwtService;
import store.mailstock.email.EmailService;
import store.mailstock.wallet.service.WalletService;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository users;
    private final EmailVerificationTokenRepository evtRepo;
    private final PasswordResetTokenRepository prtRepo;
    private final RefreshTokenRepository rtRepo;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AuthenticationManager authManager;
    private final EmailService email;
    private final WalletService walletService;
    private final store.mailstock.telegram.TelegramNotifier telegramNotifier;

    private static final SecureRandom RNG = new SecureRandom();

    @Transactional
    public void register(RegisterRequest req) {
        if (users.existsByEmailIgnoreCase(req.email()))
            throw ApiException.conflict("Email already registered");

        Role role = Role.valueOf(req.role().toUpperCase());
        User u = User.builder()
                .email(req.email().toLowerCase())
                .passwordHash(encoder.encode(req.password()))
                .fullName(req.fullName())
                .roles(new HashSet<>(Set.of(role)))
                .enabled(true).emailVerified(false).locked(false)
                .build();
        u = users.save(u);

        if (role == Role.SELLER) walletService.createForSeller(u.getId());

        telegramNotifier.adminAlert("new_user",
                "New " + role.name().toLowerCase() + " registered",
                u.getFullName() + " — " + u.getEmail());

        sendVerificationEmail(u);
    }

    /** (Re)issue a 24h verification token and email it. Shared by register and resend. */
    private void sendVerificationEmail(User u) {
        String token = randomToken();
        evtRepo.save(EmailVerificationToken.builder()
                .token(token).userId(u.getId())
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .build());
        email.sendVerification(u.getEmail(), u.getFullName(), token);
    }

    /**
     * Resend the verification email. No-op (silently) if the account doesn't exist or is already
     * verified, so the response never reveals whether an email is registered.
     */
    @Transactional
    public void resendVerification(String emailAddr) {
        users.findByEmailIgnoreCase(emailAddr)
                .filter(u -> !u.isEmailVerified())
                .ifPresent(this::sendVerificationEmail);
    }

    @Transactional
    public void verifyEmail(String token) {
        EmailVerificationToken t = evtRepo.findByToken(token)
                .orElseThrow(() -> ApiException.badRequest("Invalid token"));
        if (t.getUsedAt() != null) throw ApiException.badRequest("Token already used");
        if (t.getExpiresAt().isBefore(Instant.now())) throw ApiException.badRequest("Token expired");
        User u = users.findById(t.getUserId()).orElseThrow(() -> ApiException.notFound("User not found"));
        u.setEmailVerified(true);
        users.save(u);
        t.setUsedAt(Instant.now());
        evtRepo.save(t);
    }

    @Transactional
    public AuthResponse login(LoginRequest req) {
        Authentication auth;
        try {
            auth = authManager.authenticate(new UsernamePasswordAuthenticationToken(req.email(), req.password()));
        } catch (DisabledException e) {
            // User.isEnabled() == enabled && emailVerified, so BOTH an unverified account and an
            // admin-disabled one authenticate as "disabled". Tell them apart: if the email is already
            // verified, the account was switched off by an admin and a resend link cannot help.
            User existing = users.findByEmailIgnoreCase(req.email()).orElse(null);
            if (existing != null && existing.isEmailVerified())
                throw ApiException.forbidden("Account disabled");
            throw ApiException.forbidden("Email not verified");
        } catch (LockedException e) {
            throw ApiException.forbidden("Account locked");
        }
        User u = (User) auth.getPrincipal();
        if (!u.isEmailVerified()) throw ApiException.forbidden("Email not verified");
        u.setLastLoginAt(Instant.now());
        users.save(u);
        return issueTokens(u);
    }

    @Transactional
    public AuthResponse refresh(String refreshToken) {
        RefreshToken rt = rtRepo.findByToken(refreshToken)
                .orElseThrow(() -> ApiException.unauthorized("Invalid refresh token"));
        if (rt.getRevokedAt() != null || rt.getExpiresAt().isBefore(Instant.now()))
            throw ApiException.unauthorized("Refresh token expired");
        User u = users.findById(rt.getUserId()).orElseThrow(() -> ApiException.notFound("User not found"));
        // Account-conflict logout: if the account was locked or disabled since the last refresh
        // (e.g. an admin locked it), refuse the refresh. The guard fires on every attempt while the
        // account stays locked/disabled, so the client force-logs-out and can't slide the session.
        if (!u.isEnabled() || !u.isAccountNonLocked())
            throw ApiException.unauthorized(!u.isAccountNonLocked() ? "Account locked" : "Account disabled");
        // Rotate: revoke the used token and issue a fresh access + refresh pair (slides the 7-day window).
        rt.setRevokedAt(Instant.now());
        rtRepo.save(rt);
        return issueTokens(u);
    }

    @Transactional
    public void logout(String refreshToken) {
        rtRepo.findByToken(refreshToken).ifPresent(rt -> {
            rt.setRevokedAt(Instant.now());
            rtRepo.save(rt);
        });
    }

    @Transactional
    public void forgotPassword(String emailAddr) {
        users.findByEmailIgnoreCase(emailAddr).ifPresent(u -> {
            String token = randomToken();
            prtRepo.save(PasswordResetToken.builder()
                    .token(token).userId(u.getId())
                    .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                    .build());
            email.sendPasswordReset(u.getEmail(), u.getFullName(), token);
        });
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken t = prtRepo.findByToken(token)
                .orElseThrow(() -> ApiException.badRequest("Invalid token"));
        if (t.getUsedAt() != null) throw ApiException.badRequest("Token already used");
        if (t.getExpiresAt().isBefore(Instant.now())) throw ApiException.badRequest("Token expired");
        User u = users.findById(t.getUserId()).orElseThrow(() -> ApiException.notFound("User not found"));
        u.setPasswordHash(encoder.encode(newPassword));
        users.save(u);
        t.setUsedAt(Instant.now());
        prtRepo.save(t);
    }

    private AuthResponse issueTokens(User u) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("uid", u.getId());
        claims.put("roles", u.getRoles());
        String access = jwt.generateAccessToken(u, claims);
        String refresh = jwt.generateRefreshToken(u);
        rtRepo.save(RefreshToken.builder()
                .token(refresh).userId(u.getId())
                .expiresAt(Instant.now().plusMillis(jwt.getRefreshTtlMs()))
                .build());
        return new AuthResponse(access, refresh, u.getId(), u.getEmail(), u.getFullName(),
                u.getRoles().stream().map(Enum::name).collect(Collectors.toSet()), u.isMustChangePassword());
    }

    private String randomToken() {
        byte[] b = new byte[48];
        RNG.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}
