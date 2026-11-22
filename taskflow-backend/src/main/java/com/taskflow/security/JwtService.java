package com.taskflow.security;

import com.taskflow.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class JwtService {

    private final JwtProperties jwtProperties;
    private final SecretKey signingKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        byte[] keyBytes = decodeSecret(jwtProperties.secret());
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 256 bits (32 bytes).");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(CustomUserDetails userDetails) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusMillis(jwtProperties.accessExpirationMs());
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(userDetails.getId()))
                .claim("email", userDetails.getEmail())
                .claim("role", userDetails.getPrimaryRole().name())
                .claim("orgId", userDetails.getOrganizationId())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public Claims parseAndValidateToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isValidFormat(String token) {
        try {
            parseAndValidateToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    public String generateOpaqueRefreshToken() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String sha256Hex(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", ex);
        }
    }

    public long getAccessExpirationSeconds() {
        return jwtProperties.accessExpirationMs() / 1000L;
    }

    public long getRefreshExpirationMs() {
        return jwtProperties.refreshExpirationMs();
    }

    private byte[] decodeSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET environment variable must be configured.");
        }
        try {
            return Decoders.BASE64.decode(secret);
        } catch (RuntimeException ex) {
            return secret.getBytes(StandardCharsets.UTF_8);
        }
    }
}
