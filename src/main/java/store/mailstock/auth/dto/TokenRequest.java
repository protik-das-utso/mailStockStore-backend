package store.mailstock.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record TokenRequest(@NotBlank String token) {}
