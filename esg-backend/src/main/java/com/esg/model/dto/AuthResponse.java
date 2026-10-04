package com.esg.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Login/refresh/MFA result. Either tokens ({@code accessToken} + user) or an MFA challenge
 * ({@code mfaRequired}/{@code mfaSetupRequired} + {@code mfaToken}). The refresh token is never in the body:
 * it is set as an HttpOnly cookie by AuthController.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {
    private String accessToken;
    private String tokenType;
    private Long expiresIn;
    private UserDto user;
    private Boolean mfaRequired;
    private Boolean mfaSetupRequired;
    private String mfaToken;

    public static AuthResponse tokens(String accessToken, long expiresIn, UserDto user) {
        AuthResponse r = new AuthResponse();
        r.accessToken = accessToken;
        r.tokenType = "Bearer";
        r.expiresIn = expiresIn;
        r.user = user;
        return r;
    }

    public static AuthResponse mfaChallenge(String mfaToken, boolean setupRequired) {
        AuthResponse r = new AuthResponse();
        r.mfaToken = mfaToken;
        r.mfaRequired = !setupRequired;
        r.mfaSetupRequired = setupRequired;
        return r;
    }

    public String getAccessToken() { return accessToken; }
    public String getTokenType() { return tokenType; }
    public Long getExpiresIn() { return expiresIn; }
    public UserDto getUser() { return user; }
    public Boolean getMfaRequired() { return mfaRequired; }
    public Boolean getMfaSetupRequired() { return mfaSetupRequired; }
    public String getMfaToken() { return mfaToken; }

    /** The signed-in user as returned by login, refresh and GET /auth/me. */
    public record UserDto(String id, String email, String firstName, String lastName, String tenantId,
                          String tenantName, List<String> roles, List<String> permissions, boolean tenantWide,
                          List<String> orgScopeIds, boolean mfaEnabled, String timezone, String preferredLanguage,
                          List<String> modules) {}
}
