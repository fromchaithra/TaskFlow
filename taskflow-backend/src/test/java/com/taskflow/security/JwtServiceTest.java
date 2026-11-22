package com.taskflow.security;

import com.taskflow.config.JwtProperties;
import com.taskflow.entity.Organization;
import com.taskflow.entity.Role;
import com.taskflow.entity.User;
import com.taskflow.enums.RoleName;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties(
                "VGFza0Zsb3dUZXN0U2VjcmV0S2V5Rm9ySlVuaXQ1VGVzdGluZzEyMzQ1Njc4OTA=",
                900000L,
                604800000L,
                "tf_refresh",
                false,
                "Lax"
        );
        jwtService = new JwtService(properties);
    }

    @Test
    void generateAndValidateAccessToken_ShouldContainExpectedClaims() {
        Organization org = new Organization("TaskFlow", "taskflow", "Tech", "UTC", "MON,TUE,WED,THU,FRI");
        ReflectionTestUtils.setField(org, "id", 1L);

        Role adminRole = new Role(RoleName.ADMIN, "Admin");
        User user = new User(org, "EMP-0001", "admin@taskflow.local", "hashed", "Admin User", "Admin", adminRole);
        ReflectionTestUtils.setField(user, "id", 10L);

        CustomUserDetails details = new CustomUserDetails(user);
        String token = jwtService.generateAccessToken(details);
        Claims claims = jwtService.parseAndValidateToken(token);

        assertThat(claims.getSubject()).isEqualTo("10");
        assertThat(claims.get("email", String.class)).isEqualTo("admin@taskflow.local");
        assertThat(claims.get("role", String.class)).isEqualTo("ADMIN");
        assertThat(claims.get("orgId", Number.class).longValue()).isEqualTo(1L);
        assertThat(claims.getId()).isNotBlank();
    }

    @Test
    void constructor_ShouldRejectWeakSecret() {
        JwtProperties weak = new JwtProperties("short-secret", 900000L, 604800000L, "tf_refresh", false, "Lax");
        assertThatThrownBy(() -> new JwtService(weak))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("256 bits");
    }

    @Test
    void sha256Hex_ShouldProduceDeterministic64CharDigest() {
        String digest1 = jwtService.sha256Hex("opaque-token-value");
        String digest2 = jwtService.sha256Hex("opaque-token-value");

        assertThat(digest1).hasSize(64).isEqualTo(digest2);
    }
}
