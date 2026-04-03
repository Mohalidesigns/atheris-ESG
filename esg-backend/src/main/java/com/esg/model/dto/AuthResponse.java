package com.esg.model.dto;

import java.util.List;

public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private long expiresIn;
    private UserDto user;

    public AuthResponse() {}
    public AuthResponse(String accessToken, String refreshToken, String tokenType, long expiresIn, UserDto user) {
        this.accessToken = accessToken; this.refreshToken = refreshToken;
        this.tokenType = tokenType; this.expiresIn = expiresIn; this.user = user;
    }

    public String getAccessToken() { return accessToken; } public void setAccessToken(String v) { this.accessToken = v; }
    public String getRefreshToken() { return refreshToken; } public void setRefreshToken(String v) { this.refreshToken = v; }
    public String getTokenType() { return tokenType; } public void setTokenType(String v) { this.tokenType = v; }
    public long getExpiresIn() { return expiresIn; } public void setExpiresIn(long v) { this.expiresIn = v; }
    public UserDto getUser() { return user; } public void setUser(UserDto v) { this.user = v; }

    public static class UserDto {
        private String id, email, firstName, lastName, tenantId, role, timezone, preferredLanguage;
        private List<String> permissions;

        public UserDto() {}
        public UserDto(String id, String email, String firstName, String lastName, String tenantId,
                       String role, List<String> permissions, String timezone, String preferredLanguage) {
            this.id = id; this.email = email; this.firstName = firstName; this.lastName = lastName;
            this.tenantId = tenantId; this.role = role; this.permissions = permissions;
            this.timezone = timezone; this.preferredLanguage = preferredLanguage;
        }

        public String getId() { return id; } public String getEmail() { return email; }
        public String getFirstName() { return firstName; } public String getLastName() { return lastName; }
        public String getTenantId() { return tenantId; } public String getRole() { return role; }
        public List<String> getPermissions() { return permissions; }
        public String getTimezone() { return timezone; } public String getPreferredLanguage() { return preferredLanguage; }
    }
}
