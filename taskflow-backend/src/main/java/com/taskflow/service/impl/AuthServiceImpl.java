package com.taskflow.service.impl;

import com.taskflow.dto.auth.AuthResponse;
import com.taskflow.dto.auth.ActivateAccountRequest;
import com.taskflow.dto.auth.ActivationTokenValidationResponse;
import com.taskflow.dto.auth.AuthResult;
import com.taskflow.entity.ActivationToken;
import com.taskflow.repository.ActivationTokenRepository;
import com.taskflow.util.TokenHasher;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.taskflow.dto.auth.AuthUserDto;
import com.taskflow.dto.auth.LoginRequest;
import com.taskflow.entity.RefreshToken;
import com.taskflow.entity.RevokedToken;
import com.taskflow.entity.User;
import com.taskflow.exception.ResourceNotFoundException;
import com.taskflow.repository.RefreshTokenRepository;
import com.taskflow.repository.RevokedTokenRepository;
import com.taskflow.repository.TeamMemberRepository;
import com.taskflow.repository.UserRepository;
import com.taskflow.security.CustomUserDetails;
import com.taskflow.security.JwtService;
import com.taskflow.service.AuthService;
import com.taskflow.service.LiveSubscriptionRegistry;
import com.taskflow.util.SecurityUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final ActivationTokenRepository activationTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RevokedTokenRepository revokedTokenRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final LiveSubscriptionRegistry liveSubscriptions;

    public AuthServiceImpl(
            AuthenticationManager authenticationManager,
            UserRepository userRepository,
            ActivationTokenRepository activationTokenRepository,
            RefreshTokenRepository refreshTokenRepository,
            RevokedTokenRepository revokedTokenRepository,
            TeamMemberRepository teamMemberRepository,
            JwtService jwtService,
            PasswordEncoder passwordEncoder,
            LiveSubscriptionRegistry liveSubscriptions
    ) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.activationTokenRepository = activationTokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.revokedTokenRepository = revokedTokenRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.liveSubscriptions = liveSubscriptions;
    }

    @Override
    @Transactional
    public AuthResult login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email().trim().toLowerCase(), request.password())
        );

        CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!user.isActive()) {
            throw new DisabledException("Account is deactivated");
        }

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        String accessToken = jwtService.generateAccessToken(principal);
        String rawRefreshToken = issueAndPersistRefreshToken(user);

        AuthResponse response = AuthResponse.bearer(
                accessToken,
                jwtService.getAccessExpirationSeconds(),
                toAuthUserDto(user)
        );
        return new AuthResult(response, rawRefreshToken);
    }

    @Override
    @Transactional
    public AuthResult refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new BadCredentialsException("Refresh token missing");
        }

        String tokenHash = jwtService.sha256Hex(rawRefreshToken);
        RefreshToken existing = refreshTokenRepository.findByTokenHashWithUser(tokenHash)
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        if (existing.isRevoked() || existing.isExpired()) {
            refreshTokenRepository.revokeAllActiveForUser(existing.getUser().getId());
            throw new BadCredentialsException("Refresh token expired or revoked");
        }

        User user = existing.getUser();
        if (!user.isActive()) {
            existing.revoke();
            refreshTokenRepository.save(existing);
            throw new DisabledException("Account is deactivated");
        }

        // Rotate refresh token on every refresh
        existing.revoke();
        refreshTokenRepository.save(existing);

        CustomUserDetails userDetails = new CustomUserDetails(user);
        String newAccessToken = jwtService.generateAccessToken(userDetails);
        String newRawRefreshToken = issueAndPersistRefreshToken(user);

        AuthResponse response = AuthResponse.bearer(
                newAccessToken,
                jwtService.getAccessExpirationSeconds(),
                toAuthUserDto(user)
        );
        return new AuthResult(response, newRawRefreshToken);
    }

    @Override
    @Transactional(readOnly = true)
    public ActivationTokenValidationResponse validateActivationToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BadCredentialsException("Activation token is required");
        }
        String hash = TokenHasher.sha256Hex(rawToken.trim());
        ActivationToken token = activationTokenRepository.findByTokenHashWithUser(hash)
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired activation token"));
        if (token.isUsed() || token.isExpired()) {
            throw new BadCredentialsException("Activation token expired or already used");
        }
        User user = token.getUser();
        if (user.isActive()) {
            throw new BadCredentialsException("Account is already activated");
        }
        return new ActivationTokenValidationResponse(user.getEmail(), user.getFullName(), token.getExpiresAt());
    }

    @Override
    @Transactional
    public AuthUserDto activateAccount(ActivateAccountRequest request) {
        String hash = TokenHasher.sha256Hex(request.token().trim());
        ActivationToken token = activationTokenRepository.findByTokenHashWithUser(hash)
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired activation token"));
        if (token.isUsed() || token.isExpired()) {
            throw new BadCredentialsException("Activation token expired or already used");
        }
        User user = token.getUser();
        if (user.isActive()) {
            throw new BadCredentialsException("Account is already activated");
        }
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setActive(true);
        userRepository.save(user);

        token.markUsed();
        activationTokenRepository.save(token);
        activationTokenRepository.invalidatePendingForUser(user.getId());

        return toAuthUserDto(user);
    }

    @Override
    @Transactional
    public void logout(String authorizationHeader, String rawRefreshToken) {
        Long authenticatedUserId = null;
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            String jwt = authorizationHeader.substring(7).trim();
            try {
                Claims claims = jwtService.parseAndValidateToken(jwt);
                String jti = claims.getId();
                Long userId = Long.parseLong(claims.getSubject());
                authenticatedUserId = userId;
                if (jti != null && !revokedTokenRepository.existsByTokenJti(jti)) {
                    User user = userRepository.getReferenceById(userId);
                    Instant expiresAt = claims.getExpiration().toInstant();
                    revokedTokenRepository.save(new RevokedToken(jti, user, expiresAt));
                }
            } catch (JwtException | IllegalArgumentException ignored) {
                // Token already expired or invalid; still clear refresh cookie
            }
        }

        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            String hash = jwtService.sha256Hex(rawRefreshToken);
            refreshTokenRepository.findByTokenHashWithUser(hash).ifPresent(token -> {
                token.revoke();
                refreshTokenRepository.save(token);
            });
        }
        if (authenticatedUserId != null) {
            liveSubscriptions.closeAllForUser(authenticatedUserId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public AuthUserDto getCurrentUserProfile() {
        CustomUserDetails principal = SecurityUtils.currentUser();
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return toAuthUserDto(user);
    }

    private String issueAndPersistRefreshToken(User user) {
        String rawRefreshToken = jwtService.generateOpaqueRefreshToken();
        String hash = jwtService.sha256Hex(rawRefreshToken);
        Instant expiresAt = Instant.now().plusMillis(jwtService.getRefreshExpirationMs());
        refreshTokenRepository.save(new RefreshToken(user, hash, expiresAt));
        return rawRefreshToken;
    }

    private AuthUserDto toAuthUserDto(User user) {
        List<String> teams = teamMemberRepository.findByUserId(user.getId())
                .stream()
                .map(tm -> tm.getTeam().getName())
                .toList();

        return new AuthUserDto(
                user.getId(),
                user.getOrganization().getId(),
                user.getOrganization().getName(),
                user.getEmployeeCode(),
                user.getEmail(),
                user.getFullName(),
                user.getJobTitle(),
                user.getAvatarColor(),
                user.getPrimaryRoleName(),
                user.isActive(),
                user.getLastLoginAt(),
                teams
        );
    }
}
