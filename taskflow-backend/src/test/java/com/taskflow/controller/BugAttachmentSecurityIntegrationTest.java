package com.taskflow.controller;

import com.taskflow.config.StorageProperties;
import com.taskflow.entity.Bug;
import com.taskflow.entity.BugAttachment;
import com.taskflow.entity.Organization;
import com.taskflow.entity.Project;
import com.taskflow.entity.Role;
import com.taskflow.entity.User;
import com.taskflow.enums.BugSeverity;
import com.taskflow.enums.PriorityLevel;
import com.taskflow.enums.RoleName;
import com.taskflow.repository.BugAttachmentRepository;
import com.taskflow.repository.BugRepository;
import com.taskflow.repository.OrganizationRepository;
import com.taskflow.repository.ProjectRepository;
import com.taskflow.repository.RoleRepository;
import com.taskflow.repository.UserRepository;
import com.taskflow.security.CustomUserDetails;
import com.taskflow.security.JwtService;
import com.taskflow.support.TestDatabaseCleaner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Attachment operations must enforce the same project/organization boundary as downloads. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BugAttachmentSecurityIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private BugRepository bugRepository;
    @Autowired private BugAttachmentRepository attachmentRepository;
    @Autowired private JwtService jwtService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private StorageProperties storageProperties;

    private Organization orgA;
    private User adminA;
    private User adminB;
    private User pmB;
    private User pmOtherA;
    private Bug bugA;
    private BugAttachment attachment;
    private Path storedFile;

    @BeforeEach
    void setUp() throws Exception {
        TestDatabaseCleaner.clean(jdbcTemplate);

        orgA = organizationRepository.save(new Organization("Org A", "attach-org-a", "Software", "UTC", "MON-FRI"));
        Organization orgB = organizationRepository.save(new Organization("Org B", "attach-org-b", "Finance", "UTC", "MON-FRI"));

        Role adminRole = roleRepository.save(new Role(RoleName.ADMIN, "Admin"));
        Role pmRole = roleRepository.save(new Role(RoleName.PROJECT_MANAGER, "PM"));
        roleRepository.save(new Role(RoleName.DEVELOPER, "Developer"));
        roleRepository.save(new Role(RoleName.QA_TESTER, "QA"));

        adminA = user(orgA, "EMP-A1", "admin-a@attach.test", adminRole);
        adminB = user(orgB, "EMP-B1", "admin-b@attach.test", adminRole);
        pmB = user(orgB, "EMP-B2", "pm-b@attach.test", pmRole);
        pmOtherA = user(orgA, "EMP-A2", "pm-other-a@attach.test", pmRole);
        User pmA = user(orgA, "EMP-A3", "pm-a@attach.test", pmRole);

        Project projectA = projectRepository.save(new Project(
                orgA, "ATTA", "Project A", "Org A project", pmA,
                LocalDate.now(), LocalDate.now().plusDays(30)));
        Project projectB = projectRepository.save(new Project(
                orgB, "ATTB", "Project B", "Org B project", pmB,
                LocalDate.now(), LocalDate.now().plusDays(30)));
        bugRepository.save(new Bug(projectB, "ATTB-BUG-1", null, "Org B bug", "desc", "steps",
                BugSeverity.MEDIUM, PriorityLevel.MEDIUM, adminB, null));

        bugA = bugRepository.save(new Bug(projectA, "ATTA-BUG-1", null, "Org A bug", "desc", "steps",
                BugSeverity.MEDIUM, PriorityLevel.MEDIUM, adminA, null));

        String storedName = UUID.randomUUID() + "_evidence.txt";
        Path dir = Paths.get(storageProperties.localDir()).toAbsolutePath().normalize()
                .resolve("bugs").resolve(String.valueOf(bugA.getId()));
        Files.createDirectories(dir);
        storedFile = dir.resolve(storedName);
        Files.writeString(storedFile, "sensitive evidence");

        attachment = attachmentRepository.save(new BugAttachment(
                bugA, adminA, "evidence.txt", storedName, dir.toString(), "text/plain", 18L));
    }

    private User user(Organization org, String code, String email, Role role) {
        return userRepository.save(new User(org, code, email,
                passwordEncoder.encode("StrongPass123!"), email, "Test user", role));
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateAccessToken(new CustomUserDetails(user));
    }

    private void assertAttachmentStillPresent() {
        assertThat(attachmentRepository.findById(attachment.getId())).isPresent();
        assertThat(Files.exists(storedFile)).isTrue();
    }

    @Test
    void adminFromAnotherOrganizationCannotDeleteAttachment() throws Exception {
        mockMvc.perform(delete("/api/v1/bugs/{bugId}/attachments/{attachmentId}",
                        bugA.getId(), attachment.getId())
                        .header("Authorization", bearer(adminB)))
                .andExpect(status().isForbidden());
        assertAttachmentStillPresent();
    }

    @Test
    void projectManagerFromAnotherOrganizationCannotDeleteAttachment() throws Exception {
        mockMvc.perform(delete("/api/v1/bugs/{bugId}/attachments/{attachmentId}",
                        bugA.getId(), attachment.getId())
                        .header("Authorization", bearer(pmB)))
                .andExpect(status().isForbidden());
        assertAttachmentStillPresent();
    }

    @Test
    void projectManagerOfUnrelatedProjectInSameOrganizationCannotDeleteAttachment() throws Exception {
        mockMvc.perform(delete("/api/v1/bugs/{bugId}/attachments/{attachmentId}",
                        bugA.getId(), attachment.getId())
                        .header("Authorization", bearer(pmOtherA)))
                .andExpect(status().isForbidden());
        assertAttachmentStillPresent();
    }

    @Test
    void crossOrganizationDownloadAndMetadataAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/bugs/{bugId}/attachments/{attachmentId}/download",
                        bugA.getId(), attachment.getId())
                        .header("Authorization", bearer(adminB)))
                .andExpect(status().isForbidden());
        assertAttachmentStillPresent();
    }

    @Test
    void authorizedAdminCanDeleteAttachmentAndStoredFile() throws Exception {
        mockMvc.perform(delete("/api/v1/bugs/{bugId}/attachments/{attachmentId}",
                        bugA.getId(), attachment.getId())
                        .header("Authorization", bearer(adminA)))
                .andExpect(status().isNoContent());
        assertThat(attachmentRepository.findById(attachment.getId())).isEmpty();
        assertThat(Files.exists(storedFile)).isFalse();
    }
}
