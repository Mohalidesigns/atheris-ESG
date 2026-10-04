package com.esg.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Request/response bodies for /auth/mfa/* (TRD §5.4.1). */
public final class MfaRequests {
    private MfaRequests() {}

    public record Setup(@NotBlank String mfaToken) {}

    public record SetupResponse(String secret, String otpauthUri) {}

    public record Verify(@NotBlank String mfaToken,
                         @NotBlank @Pattern(regexp = "\\d{6}", message = "must be a 6-digit code") String code) {}
}
