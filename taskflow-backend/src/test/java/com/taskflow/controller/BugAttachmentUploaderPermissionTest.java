package com.taskflow.controller;

import com.taskflow.entity.Bug;
import com.taskflow.entity.Organization;
import com.taskflow.entity.Project;
import com.taskflow.entity.ProjectMember;
import com.taskflow.entity.Role;
import com.taskflow.entity.User;
import com.taskflow.enums.BugSeverity;
import com.taskflow.enums.PriorityLevel;
import com.taskflow.enums.RoleName;
import com.taskflow.repository.BugAttachmentRepository;
import com.taskflow.repository.BugRepository;
import com.taskflow.repository.OrganizationRepository;
import com.taskflow.repository.ProjectMemberRepository;
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
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A developer who uploaded an attachment must be able to delete their own file. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BugAttachmentUploaderPermissionTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper jsonMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectMemberRepository projectMemberRepository;
    @Autowired private BugRepository bugRepository;
    @Autowired private BugAttachmentRepository attachmentRepository;
    @Autowired private JwtService jwtService;
    @Autowired private PasswordEncoder passwordEncoder;

    private Bug bug;
    private String developerBearer;

    @BeforeEach
    void setUp() {
        TestDatabaseCleaner.clean(jdbcTemplate);
        Organization org = organizationRepository.save(
                new Organization("Org", "uploader-perm-org", "Software", "UTC", "MON-FRI"));
        Role adminRole = roleRepository.save(new Role(RoleName.ADMIN, "Admin"));
        Role devRole = roleRepository.save(new Role(RoleName.DEVELOPER, "Developer"));
        roleRepository.save(new Role(RoleName.PROJECT_MANAGER, "PM"));
        roleRepository.save(new Role(RoleName.QA_TESTER, "QA"));

        User admin = userRepository.save(new User(org, "EMP-1", "admin@upload.test",
                passwordEncoder.encode("StrongPass123!"), "Admin", "Admin", adminRole));
        User developer = userRepository.save(new User(org, "EMP-2", "dev@upload.test",
                passwordEncoder.encode("StrongPass123!"), "Dev", "Developer", devRole));
        Project project = projectRepository.save(new Project(org, "UPL", "Project", "", admin,
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(30)));
        projectMemberRepository.save(new ProjectMember(project, developer, RoleName.DEVELOPER, admin));
        bug = bugRepository.save(new Bug(project, "UPL-B1", null, "Bug", "desc", "steps",
                BugSeverity.MEDIUM, PriorityLevel.MEDIUM, admin, developer));
        developerBearer = "Bearer " + jwtService.generateAccessToken(new CustomUserDetails(developer));
    }

    @Test
    void developerUploaderCanDeleteOwnAttachment() throws Exception {
        String body = mockMvc.perform(multipart("/api/v1/bugs/{id}/attachments", bug.getId())
                        .file(new MockMultipartFile("file", "mine.txt", "text/plain", "payload".getBytes(StandardCharsets.UTF_8)))
                        .header(HttpHeaders.AUTHORIZATION, developerBearer))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long attachmentId = jsonMapper.readTree(body).get("id").asLong();
        assertThat(attachmentRepository.findById(attachmentId)).isPresent();

        mockMvc.perform(delete("/api/v1/bugs/{bugId}/attachments/{attachmentId}", bug.getId(), attachmentId)
                        .header(HttpHeaders.AUTHORIZATION, developerBearer))
                .andExpect(status().isNoContent());

        assertThat(attachmentRepository.findById(attachmentId)).isEmpty();
    }
}
