package com.esg.controller;

import com.esg.exception.ApiException;
import com.esg.model.dto.ApiResponse;
import com.esg.model.dto.AuthResponse;
import com.esg.model.dto.LoginRequest;
import com.esg.model.dto.MfaRequests;
import com.esg.security.PublicEndpoint;
import com.esg.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoints (TRD §5.4, §6.4.2). The refresh token travels only in an HttpOnly, SameSite=Strict
 * cookie scoped to /api/v1/auth, so page scripts can't read it. Non-browser clients may send it in the body.
 */
@RestController
@RequestMapping("/v1/auth")
public class AuthController {

    static final String REFRESH_COOKIE = "esg_refresh";
    private static final String COOKIE_PATH = "/api/v1/auth";

    private final AuthService authService;
    private final boolean secureCookie;
    private final Duration refreshTtl;

    public AuthController(AuthService authService,
                          @Value("${esg.security.cookie-secure:true}") boolean secureCookie,
                          @Value("${esg.security.refresh-expiration-ms}") long refreshExpirationMs) {
        this.authService = authService;
        this.secureCookie = secureCookie;
        this.refreshTtl = Duration.ofMillis(refreshExpirationMs);
    }

    @PostMapping("/login")
    @PublicEndpoint("Credentials are checked here")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request,
                                                           HttpServletRequest http) {
        return withSession(authService.login(request, client(http)));
    }

    @PostMapping("/mfa/setup")
    @PublicEndpoint("Authorised by the short-lived MFA token from login")
    public ResponseEntity<ApiResponse<MfaRequests.SetupResponse>> setupMfa(@Valid @RequestBody MfaRequests.Setup request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.setupMfa(request)));
    }

    @PostMapping("/mfa/verify")
    @PublicEndpoint("Authorised by the short-lived MFA token from login")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyMfa(@Valid @RequestBody MfaRequests.Verify request,
                                                               HttpServletRequest http) {
        return withSession(authService.verifyMfa(request, client(http)));
    }

    @PostMapping("/refresh")
    @PublicEndpoint("Authorised by the refresh token")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String cookieToken,
            @RequestBody(required = false) Map<String, String> body, HttpServletRequest http) {
        String token = cookieToken != null ? cookieToken : body == null ? null : body.get("refreshToken");
        if (token == null) {
            return ResponseEntity.status(401).header(HttpHeaders.SET_COOKIE, clearCookie())
                    .body(ApiResponse.error("No active session"));
        }
        try {
            return withSession(authService.refresh(token, client(http)));
        } catch (ApiException e) {
            // A refresh token that didn't work is cleared so the browser stops sending it.
            return ResponseEntity.status(e.getStatus()).header(HttpHeaders.SET_COOKIE, clearCookie())
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/logout")
    @PublicEndpoint("Revokes the presented refresh token; works even after the access token expired")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = REFRESH_COOKIE, required = false) String cookieToken,
            @RequestBody(required = false) Map<String, String> body) {
        authService.logout(cookieToken != null ? cookieToken : body == null ? null : body.get("refreshToken"));
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, clearCookie())
                .body(ApiResponse.ok(null, "Logged out successfully"));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<AuthResponse.UserDto>> me() {
        return ResponseEntity.ok(ApiResponse.ok(authService.me()));
    }

    // ------------------------------------------------------------------ helpers

    private ResponseEntity<ApiResponse<AuthResponse>> withSession(AuthService.AuthResult result) {
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (result.refreshToken() != null) {
            response.header(HttpHeaders.SET_COOKIE, ResponseCookie.from(REFRESH_COOKIE, result.refreshToken())
                    .httpOnly(true).secure(secureCookie).sameSite("Strict").path(COOKIE_PATH).maxAge(refreshTtl)
                    .build().toString());
        }
        return response.body(ApiResponse.ok(result.body()));
    }

    private String clearCookie() {
        return ResponseCookie.from(REFRESH_COOKIE, "").httpOnly(true).secure(secureCookie).sameSite("Strict")
                .path(COOKIE_PATH).maxAge(0).build().toString();
    }

    private static AuthService.ClientInfo client(HttpServletRequest http) {
        return new AuthService.ClientInfo(http.getHeader(HttpHeaders.USER_AGENT), http.getRemoteAddr());
    }
}
