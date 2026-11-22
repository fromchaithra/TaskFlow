package com.taskflow.service;

import com.taskflow.dto.organization.UpdateOrganizationRequest;
import com.taskflow.entity.Organization;
import com.taskflow.entity.Role;
import com.taskflow.entity.User;
import com.taskflow.enums.RoleName;
import com.taskflow.exception.BusinessRuleException;
import com.taskflow.repository.OrganizationRepository;
import com.taskflow.repository.RoleRepository;
import com.taskflow.repository.UserRepository;
import com.taskflow.security.CustomUserDetails;
import com.taskflow.support.TestDatabaseCleaner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class OrganizationWorkingDaysValidationTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private OrganizationService organizationService;
    @Autowired private PasswordEncoder passwordEncoder;

    private Organization org;

    @BeforeEach
    void setUp() {
        TestDatabaseCleaner.clean(jdbcTemplate);
        org = organizationRepository.save(new Organization("Org", "working-days-org", "Software", "UTC", "MON,TUE,WED,THU,FRI"));
        Role adminRole = roleRepository.save(new Role(RoleName.ADMIN, "Admin"));
        User admin = userRepository.save(new User(org, "EMP-0001", "admin@working.test",
                passwordEncoder.encode("StrongPass123!"), "Admin", "Admin", adminRole));
        CustomUserDetails principal = new CustomUserDetails(admin);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private UpdateOrganizationRequest request(String workingDays) {
        return new UpdateOrganizationRequest("Org", null, "UTC", workingDays);
    }

    @Test
    void validWeekdaysAreAcceptedAndNormalized() {
        assertThat(organizationService.updateCurrentOrganization(request("mon, wed,fri")).workingDays())
                .isEqualTo("MON,WED,FRI");
        assertThat(organizationService.updateCurrentOrganization(request("SAT,SUN")).workingDays())
                .isEqualTo("SAT,SUN");
    }

    @Test
    void invalidWeekdayIsRejected() {
        assertThatThrownBy(() -> organizationService.updateCurrentOrganization(request("MON,FUN")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Invalid working day");
    }

    @Test
    void duplicateWeekdayIsRejected() {
        assertThatThrownBy(() -> organizationService.updateCurrentOrganization(request("MON,MON")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Duplicate working day");
    }

    @Test
    void emptyAndMalformedValuesAreRejected() {
        assertThatThrownBy(() -> organizationService.updateCurrentOrganization(request("")))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> organizationService.updateCurrentOrganization(request("MON,")))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> organizationService.updateCurrentOrganization(request("MON;;TUE")))
                .isInstanceOf(BusinessRuleException.class);
    }
}
