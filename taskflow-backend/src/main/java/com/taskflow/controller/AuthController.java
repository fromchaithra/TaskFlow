package com.taskflow.controller;

import com.taskflow.config.CorsProperties;
import com.taskflow.config.JwtProperties;
import com.taskflow.dto.auth.ActivateAccountRequest;
import com.taskflow.dto.auth.ActivationTokenValidationResponse;
import com.taskflow.dto.auth.AuthResponse;
import com.taskflow.dto.auth.AuthResult;
import com.taskflow.dto.auth.AuthUserDto;
import com.taskflow.dto.auth.LoginRequest;
import com.taskflow.exception.UnauthorizedResourceAccessException;
import com.taskflow.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Arrays;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtProperties jwtProperties;
    private final CorsProperties corsProperties;

    public AuthController(
            AuthService authService,
            JwtProperties jwtProperties,
            CorsProperties corsProperties
    ) {
        this.authService = authService;
        this.jwtProperties = jwtProperties;
        this.corsProperties = corsProperties;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResult result = authService.login(request);
        ResponseCookie cookie = buildRefreshCookie(
                result.rawRefreshToken(),
                Duration.ofMillis(jwtProperties.refreshExpirationMs())
        );
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(result.response());
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest request) {
        verifyCookieCsrfOrigin(request);
        String rawRefreshToken = extractRefreshCookie(request);
        AuthResult result = authService.refresh(rawRefreshToken);
        ResponseCookie cookie = buildRefreshCookie(
                result.rawRefreshToken(),
                Duration.ofMillis(jwtProperties.refreshExpirationMs())
        );
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(result.response());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
            HttpServletRequest request
    ) {
        String rawRefreshToken = extractRefreshCookie(request);
        authService.logout(authHeader, rawRefreshToken);
        ResponseCookie clearedCookie = buildRefreshCookie("", Duration.ZERO);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearedCookie.toString())
                .build();
    }

    @GetMapping("/me")
    public ResponseEntity<AuthUserDto> me() {
        return ResponseEntity.ok(authService.getCurrentUserProfile());
    }

    @GetMapping("/activate/validate")
    public ResponseEntity<ActivationTokenValidationResponse> validateActivationToken(
            @RequestParam("token") String token
    ) {
        return ResponseEntity.ok(authService.validateActivationToken(token));
    }

    @PostMapping("/activate")
    public ResponseEntity<AuthUserDto> activateAccount(@Valid @RequestBody ActivateAccountRequest request) {
        return ResponseEntity.ok(authService.activateAccount(request));
    }

    private ResponseCookie buildRefreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(jwtProperties.cookieName(), value)
                .httpOnly(true)
                .secure(jwtProperties.cookieSecure())
                .sameSite(jwtProperties.cookieSameSite())
                .path("/api/v1/auth")
                .maxAge(maxAge)
                .build();
    }

    private String extractRefreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(c -> jwtProperties.cookieName().equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private void verifyCookieCsrfOrigin(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin != null && !corsProperties.allowedOriginList().contains(origin)) {
            throw new UnauthorizedResourceAccessException("Origin not permitted for token refresh.");
        }
        String requestedWith = request.getHeader("X-Requested-With");
        if (!"TaskFlow".equals(requestedWith)) {
            throw new UnauthorizedResourceAccessException("Missing required CSRF verification header.");
        }
    }
}
