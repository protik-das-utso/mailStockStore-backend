package store.mailstock.auth.dto;

import java.util.Set;

public record AuthResponse(String accessToken, String refreshToken, Long userId, String email, String fullName,
                           Set<String> roles, boolean mustChangePassword) {}
