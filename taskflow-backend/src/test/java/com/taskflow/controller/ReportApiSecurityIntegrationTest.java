package com.taskflow.controller;

import com.taskflow.dto.analytics.AnalyticsGranularity;
import com.taskflow.dto.report.ReportMetadata;
import com.taskflow.dto.report.ReportType;
import com.taskflow.dto.report.model.AnalyticsReportData;
import com.taskflow.entity.Organization;
import com.taskflow.entity.Role;
import com.taskflow.entity.User;
import com.taskflow.enums.RoleName;
import com.taskflow.exception.UnauthorizedResourceAccessException;
import com.taskflow.repository.OrganizationRepository;
import com.taskflow.repository.RoleRepository;
import com.taskflow.repository.UserRepository;
import com.taskflow.security.CustomUserDetails;
import com.taskflow.security.JwtService;
import com.taskflow.service.AnalyticsReportService;
import com.taskflow.service.report.ExcelReportRenderer;
import com.taskflow.service.report.PdfReportRenderer;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportApiSecurityIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtService jwtService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean private AnalyticsReportService reportService;
    @MockitoBean private PdfReportRenderer pdfRenderer;
    @MockitoBean private ExcelReportRenderer excelRenderer;

    private User developer;
    private AnalyticsReportData reportData;

    @BeforeEach
    void setUp() {
        TestDatabaseCleaner.clean(jdbcTemplate);
        Organization org = organizationRepository.save(new Organization(
                "TaskFlow Enterprise", "taskflow-secure", "Software", "UTC", "MON-FRI"));
        Role developerRole = roleRepository.save(new Role(RoleName.DEVELOPER, "Developer"));
        developer = userRepository.save(new User(
                org, "EMP-3001", "dev@reports.test", passwordEncoder.encode("StrongPass123!"),
                "Dev User", "Engineer", developerRole));

        ReportMetadata metadata = new ReportMetadata(
                org.getName(), org.getSlug(), org.getIndustry(), org.getTimezone(),
                "Executive Overview", Instant.parse("2026-11-21T10:15:00Z"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                AnalyticsGranularity.WEEK, false, "test", List.of(), Map.of());
        reportData = new AnalyticsReportData(metadata, ReportType.EXECUTIVE_OVERVIEW,
                List.of(), List.of(), List.of(), false);
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateAccessToken(new CustomUserDetails(user));
    }

    private static String requestJson(String type, String format) {
        return """
                {"type":"%s","format":"%s","query":{"from":"2026-01-01","to":"2026-03-31",
                "granularity":"WEEK","projectIds":[],"teamIds":[],"employeeIds":[]},"sprintId":null}
                """.formatted(type, format);
    }

    @Test
    void unauthenticatedPdfDownloadIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/reports/pdf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("EXECUTIVE_OVERVIEW", "PDF")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unauthenticatedCatalogIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/reports/catalog"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedDeveloperCanGenerateReport() throws Exception {
        when(reportService.buildReport(any())).thenReturn(reportData);
        when(pdfRenderer.render(reportData)).thenReturn("%PDF-1.7".getBytes());

        mockMvc.perform(post("/api/v1/reports/pdf")
                        .header(HttpHeaders.AUTHORIZATION, bearer(developer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("EXECUTIVE_OVERVIEW", "PDF")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"taskflow-executive-overview-20261121-101500.pdf\""));

        verify(pdfRenderer).render(reportData);
    }

    @Test
    void authenticatedDeveloperCanReadCatalog() throws Exception {
        mockMvc.perform(get("/api/v1/reports/catalog")
                        .header(HttpHeaders.AUTHORIZATION, bearer(developer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportTypes.length()").value(10));
    }

    @Test
    void unauthorizedProjectFilterPropagatesForbidden() throws Exception {
        when(reportService.buildReport(any()))
                .thenThrow(new UnauthorizedResourceAccessException("Project not visible"));

        mockMvc.perform(post("/api/v1/reports/pdf")
                        .header(HttpHeaders.AUTHORIZATION, bearer(developer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"PROJECT_PERFORMANCE","format":"PDF",
                                "query":{"from":"2026-01-01","to":"2026-03-31","granularity":"WEEK",
                                "projectIds":[999],"teamIds":[],"employeeIds":[]}}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void crossOrganizationFilterPropagatesForbidden() throws Exception {
        when(reportService.buildReport(any()))
                .thenThrow(new UnauthorizedResourceAccessException("Team belongs to another organization"));

        mockMvc.perform(post("/api/v1/reports/xlsx")
                        .header(HttpHeaders.AUTHORIZATION, bearer(developer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"TEAM_PRODUCTIVITY","format":"XLSX",
                                "query":{"from":"2026-01-01","to":"2026-03-31","granularity":"WEEK",
                                "projectIds":[],"teamIds":[999],"employeeIds":[]}}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void sprintBurndownAuthorizationFailurePropagatesForbidden() throws Exception {
        when(reportService.buildReport(any()))
                .thenThrow(new UnauthorizedResourceAccessException("Sprint is outside project filters"));

        String withSprint = """
                {"type":"SPRINT_BURNOWN","format":"PDF",
                "query":{"from":"2026-01-01","to":"2026-03-31","granularity":"WEEK",
                "projectIds":[],"teamIds":[],"employeeIds":[]},"sprintId":7}
                """;
        mockMvc.perform(post("/api/v1/reports/pdf")
                        .header(HttpHeaders.AUTHORIZATION, bearer(developer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withSprint))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void sprintBurndownSucceedsWhenServiceAuthorizes() throws Exception {
        when(reportService.buildReport(any())).thenReturn(reportData);
        when(pdfRenderer.render(reportData)).thenReturn("%PDF".getBytes());

        String withSprint = """
                {"type":"SPRINT_BURNOWN","format":"PDF",
                "query":{"from":"2026-01-01","to":"2026-03-31","granularity":"WEEK",
                "projectIds":[],"teamIds":[],"employeeIds":[]},"sprintId":7}
                """;
        mockMvc.perform(post("/api/v1/reports/pdf")
                        .header(HttpHeaders.AUTHORIZATION, bearer(developer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withSprint))
                .andExpect(status().isOk());
    }
}
