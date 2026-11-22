package com.taskflow.service.impl;

import com.taskflow.dto.auth.AuthResponse;
import com.taskflow.dto.auth.ActivateAccountRequest;
import com.taskflow.dto.auth.ActivationTokenValidationResponse;
import com.taskflow.dto.auth.AuthResult;
import com.taskflow.entity.ActivationToken;
import com.taskflow.entity.Organization;
import com.taskflow.repository.ActivationTokenRepository;
import com.taskflow.util.TokenHasher;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.taskflow.dto.auth.AuthUserDto;
import com.taskflow.dto.auth.LoginRequest;
import com.taskflow.entity.RefreshToken;
import com.taskflow.entity.RevokedToken;
import com.taskflow.entity.User;
import com.taskflow.enums.AuditAction;
import com.taskflow.exception.ResourceNotFoundException;
import com.taskflow.repository.RefreshTokenRepository;
import com.taskflow.repository.RevokedTokenRepository;
import com.taskflow.repository.TeamMemberRepository;
import com.taskflow.repository.UserRepository;
import com.taskflow.security.CustomUserDetails;
import com.taskflow.security.JwtService;
import com.taskflow.service.AuditLogWriter;
import com.taskflow.service.AuthService;
import com.taskflow.service.LiveSubscriptionRegistry;
import com.taskflow.util.SecurityUtils;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.jsonwebtoken.JwtException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final ActivationTokenRepository activationTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RevokedTokenRepository revokedTokenRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final LiveSubscriptionRegistry liveSubscriptions;
    private final AuditLogWriter auditLogWriter;

    public AuthServiceImpl(
            AuthenticationManager authenticationManager,
            UserRepository userRepository,
            ActivationTokenRepository activationTokenRepository,
            RefreshTokenRepository refreshTokenRepository,
            RevokedTokenRepository revokedTokenRepository,
            TeamMemberRepository teamMemberRepository,
            JwtService jwtService,
            PasswordEncoder passwordEncoder,
            LiveSubscriptionRegistry liveSubscriptions,
            AuditLogWriter auditLogWriter
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
        this.auditLogWriter = auditLogWriter;
    }

    @Override
    @Transactional
    public AuthResult login(LoginRequest request) {
        String email = request.email() == null ? "" : request.email().trim().toLowerCase();
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password())
            );
        } catch (AuthenticationException ex) {
            auditAuthenticationFailure(email,
                    ex instanceof DisabledException ? AuditAction.ACCOUNT_DEACTIVATED : AuditAction.LOGIN_FAILURE);
            throw ex;
        }

        CustomUserDetails principal = (CustomUserDetails) authentication.getPrincipal();
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!user.isActive()) {
            safeAudit(user.getOrganization(), user, AuditAction.ACCOUNT_DEACTIVATED,
                    user.getId(), Map.of("method", "PASSWORD"));
            throw new DisabledException("Account is deactivated");
        }

        safeAudit(user.getOrganization(), user, AuditAction.LOGIN_SUCCESS,
                user.getId(), Map.of("method", "PASSWORD"));
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
    // Revocation performed on replay / deactivation must survive the authentication
    // exception that immediately follows; otherwise the transaction rolls it back.
    @Transactional(noRollbackFor = {BadCredentialsException.class, DisabledException.class})
    public AuthResult refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new BadCredentialsException("Refresh token missing");
        }

        String tokenHash = jwtService.sha256Hex(rawRefreshToken);
        RefreshToken existing = refreshTokenRepository.findByTokenHashWithUser(tokenHash)
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        if (existing.isRevoked() || existing.isExpired()) {
            User replayUser = existing.getUser();
            safeAudit(replayUser.getOrganization(), replayUser,
                    AuditAction.REFRESH_TOKEN_REUSE, replayUser.getId(),
                    Map.of("reason", existing.isRevoked() ? "REVOKED" : "EXPIRED"));
            refreshTokenRepository.revokeAllActiveForUser(replayUser.getId());
            throw new BadCredentialsException("Refresh token expired or revoked");
        }

        User user = existing.getUser();
        if (!user.isActive()) {
            safeAudit(user.getOrganization(), user, AuditAction.ACCOUNT_DEACTIVATED,
                    user.getId(), Map.of("method", "REFRESH_TOKEN"));
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
        safeAudit(user.getOrganization(), user, AuditAction.ACCOUNT_ACTIVATED,
                user.getId(), Map.of("method", "ACTIVATION_TOKEN"));
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

    /** Audit must never break an authentication flow, even if the writer bean fails outright. */
    private void safeAudit(Organization organization, User actor, AuditAction action,
                           Long entityId, Object newValue) {
        try {
            auditLogWriter.safeRecord(organization, actor, action, entityId, newValue);
        } catch (RuntimeException ex) {
            log.warn("Authentication audit event {} was skipped: {}", action, ex.getMessage());
        }
    }

    /** Unknown emails have no organization, which audit rows require; skip those events. */
    private void auditAuthenticationFailure(String email, AuditAction action) {
        if (email == null || email.isBlank()) return;
        try {
            userRepository.findByEmailIgnoreCase(email).ifPresent(user ->
                    safeAudit(user.getOrganization(), user, action,
                            user.getId(), Map.of("method", "PASSWORD")));
        } catch (RuntimeException ex) {
            log.warn("Authentication audit lookup for {} was skipped: {}", action, ex.getMessage());
        }
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
