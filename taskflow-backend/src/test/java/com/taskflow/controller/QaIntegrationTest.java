package com.taskflow.controller;

import com.taskflow.entity.Organization;
import com.taskflow.entity.Project;
import com.taskflow.entity.ProjectMember;
import com.taskflow.entity.Role;
import com.taskflow.entity.User;
import com.taskflow.enums.RoleName;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QaIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private ProjectMemberRepository projectMemberRepository;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Organization org;
    private User admin;
    private User qa;
    private User dev;
    private Project project;

    @BeforeEach
    void setUp() {
        TestDatabaseCleaner.clean(jdbcTemplate);

        org = organizationRepository.save(new Organization("QA Org", "qa-org", "Software", "UTC", "MON,TUE,WED,THU,FRI"));
        Role adminRole = roleRepository.save(new Role(RoleName.ADMIN, "Admin"));
        Role qaRole = roleRepository.save(new Role(RoleName.QA_TESTER, "QA Tester"));
        Role devRole = roleRepository.save(new Role(RoleName.DEVELOPER, "Developer"));

        String hash = passwordEncoder.encode("StrongPass123!");
        admin = userRepository.save(new User(org, "EMP-Q1", "admin@qa.test", hash, "Admin QA", "Admin", adminRole));
        qa = userRepository.save(new User(org, "EMP-Q2", "qa@qa.test", hash, "Quinn QA", "QA", qaRole));
        dev = userRepository.save(new User(org, "EMP-Q3", "dev@qa.test", hash, "Dana Dev", "Dev", devRole));

        project = projectRepository.save(new Project(org, "QAPR", "QA Project", "desc", admin, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)));
        projectMemberRepository.save(new ProjectMember(project, admin, RoleName.ADMIN, admin));
        projectMemberRepository.save(new ProjectMember(project, qa, RoleName.QA_TESTER, admin));
        projectMemberRepository.save(new ProjectMember(project, dev, RoleName.DEVELOPER, admin));
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateAccessToken(new com.taskflow.security.CustomUserDetails(user));
    }

    @Test
    void createTestCase_ShouldReturn201() throws Exception {
        String body = """
                {
                  "projectId": %d,
                  "title": "Login works",
                  "description": "Verify login",
                  "priority": "HIGH",
                  "steps": [
                    {"action": "Open login", "expectedResult": "Form shown"}
                  ]
                }
                """.formatted(project.getId());

        mockMvc.perform(post("/api/v1/test-cases")
                        .header(HttpHeaders.AUTHORIZATION, bearer(qa))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Login works"))
                .andExpect(jsonPath("$.testKey").exists());
    }

    @Test
    void createTestRun_AndRecordExecution() throws Exception {
        // create test case
        String tcBody = """
                {"projectId": %d, "title": "TC1", "priority": "MEDIUM",
                 "steps": [{"action":"a","expectedResult":"b"}]}
                """.formatted(project.getId());
        String tcResp = mockMvc.perform(post("/api/v1/test-cases")
                        .header(HttpHeaders.AUTHORIZATION, bearer(qa))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tcBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long tcId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(tcResp).get("id").asLong();

        // create run
        String runBody = """
                {"projectId": %d, "name": "Run1", "testCaseIds": [%d]}
                """.formatted(project.getId(), tcId);
        String runResp = mockMvc.perform(post("/api/v1/test-runs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(qa))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(runBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long runId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(runResp).get("id").asLong();

        // list executions
        String execList = mockMvc.perform(get("/api/v1/test-runs/" + runId + "/executions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(qa)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long execId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(execList).get(0).get("id").asLong();

        // record FAIL
        mockMvc.perform(patch("/api/v1/test-runs/" + runId + "/executions/" + execId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(qa))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":\"FAIL\",\"actualResult\":\"broken\",\"notes\":\"n\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("FAIL"));
    }

    @Test
    void createBugFromFailedExecution() throws Exception {
        String tcBody = """
                {"projectId": %d, "title": "TC2", "priority": "MEDIUM",
                 "steps": [{"action":"a","expectedResult":"b"}]}
                """.formatted(project.getId());
        String tcResp = mockMvc.perform(post("/api/v1/test-cases")
                        .header(HttpHeaders.AUTHORIZATION, bearer(qa))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tcBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long tcId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(tcResp).get("id").asLong();

        String runBody = """
                {"projectId": %d, "name": "Run2", "testCaseIds": [%d]}
                """.formatted(project.getId(), tcId);
        String runResp = mockMvc.perform(post("/api/v1/test-runs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(qa))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(runBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long runId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(runResp).get("id").asLong();

        String execList = mockMvc.perform(get("/api/v1/test-runs/" + runId + "/executions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(qa)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long execId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(execList).get(0).get("id").asLong();

        mockMvc.perform(patch("/api/v1/test-runs/" + runId + "/executions/" + execId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(qa))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":\"FAIL\",\"actualResult\":\"broken\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/test-runs/" + runId + "/executions/" + execId + "/create-bug")
                        .header(HttpHeaders.AUTHORIZATION, bearer(qa)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bugId").exists());
    }

    @Test
    void developerCannotCreateTestCase() throws Exception {
        String body = """
                {"projectId": %d, "title": "TC3", "priority": "MEDIUM"}
                """.formatted(project.getId());
        mockMvc.perform(post("/api/v1/test-cases")
                        .header(HttpHeaders.AUTHORIZATION, bearer(dev))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedAccessReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/test-cases"))
                .andExpect(status().isUnauthorized());
    }
}
