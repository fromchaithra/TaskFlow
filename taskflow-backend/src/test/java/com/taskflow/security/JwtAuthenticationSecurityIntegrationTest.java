package com.taskflow.security;

import com.taskflow.config.JwtProperties;
import com.taskflow.entity.Organization;
import com.taskflow.entity.Role;
import com.taskflow.entity.User;
import com.taskflow.enums.RoleName;
import com.taskflow.repository.OrganizationRepository;
import com.taskflow.repository.RoleRepository;
import com.taskflow.repository.UserRepository;
import com.taskflow.support.TestDatabaseCleaner;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JwtAuthenticationSecurityIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtService jwtService;
    @Autowired private JwtProperties jwtProperties;
    @Autowired private PasswordEncoder passwordEncoder;

    private User user;

    @BeforeEach
    void setUp() {
        TestDatabaseCleaner.clean(jdbcTemplate);
        Organization org = organizationRepository.save(
                new Organization("JWT Org", "jwt-security-org", "Software", "UTC", "MON-FRI"));
        Role adminRole = roleRepository.save(new Role(RoleName.ADMIN, "Admin"));
        roleRepository.save(new Role(RoleName.DEVELOPER, "Developer"));
        user = userRepository.save(new User(org, "EMP-0001", "jwt@security.test",
                passwordEncoder.encode("StrongPass123!"), "JWT User", "Tester", adminRole));
    }

    private String tokenFor(User target) {
        return jwtService.generateAccessToken(new CustomUserDetails(target));
    }

    private String expiredToken(Long userId) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(Date.from(Instant.now().minusSeconds(120)))
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.secret())), Jwts.SIG.HS256)
                .compact();
    }

    private void expectStatus(String token, int status) throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().is(status));
    }

    @Test
    void validUserAndTokenSucceed() throws Exception {
        expectStatus(tokenFor(user), 200);
    }

    @Test
    void deletedUserWithPreviouslyValidTokenReturns401() throws Exception {
        String token = tokenFor(user);
        userRepository.delete(user);
        expectStatus(token, 401);
    }

    @Test
    void disabledUserReturns401() throws Exception {
        String token = tokenFor(user);
        user.setActive(false);
        userRepository.save(user);
        expectStatus(token, 401);
    }

    @Test
    void invalidTokenReturns401() throws Exception {
        expectStatus("not-a-jwt", 401);
    }

    @Test
    void expiredTokenReturns401() throws Exception {
        expectStatus(expiredToken(user.getId()), 401);
    }

    @Test
    void tokenForMissingUserReturns401() throws Exception {
        // Validly signed token for a non-existent user id.
        String token = Jwts.builder()
                .subject("999999")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.secret())), Jwts.SIG.HS256)
                .compact();
        expectStatus(token, 401);
    }
}
