package com.taskflow.controller;

import com.taskflow.dto.analytics.AnalyticsGranularity;
import com.taskflow.dto.report.AnalyticsReportRequest;
import com.taskflow.dto.report.ReportMetadata;
import com.taskflow.dto.report.ReportType;
import com.taskflow.dto.report.model.AnalyticsReportData;
import com.taskflow.exception.BusinessRuleException;
import com.taskflow.exception.ResourceNotFoundException;
import com.taskflow.exception.UnauthorizedResourceAccessException;
import com.taskflow.exception.GlobalExceptionHandler;
import com.taskflow.service.AnalyticsReportService;
import com.taskflow.service.report.ExcelReportRenderer;
import com.taskflow.service.report.PdfReportRenderer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReportControllerTest {

    @Mock private AnalyticsReportService reportService;
    @Mock private PdfReportRenderer pdfRenderer;
    @Mock private ExcelReportRenderer excelRenderer;

    private MockMvc mockMvc;
    private AnalyticsReportData reportData;

    @BeforeEach
    void setUp() {
        ReportMetadata metadata = new ReportMetadata(
                "TaskFlow Enterprise", "taskflow-enterprise", "Software", "UTC",
                "Executive Overview", Instant.parse("2026-11-21T10:15:00Z"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                AnalyticsGranularity.WEEK, false, "test basis",
                List.of("Granularity: WEEK"), Map.of());
        reportData = new AnalyticsReportData(metadata, ReportType.EXECUTIVE_OVERVIEW,
                List.of(), List.of(), List.of(), false);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ReportController(reportService, pdfRenderer, excelRenderer))
                .setValidator(validator)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static String requestJson(String type, String format) {
        return """
                {"type":"%s","format":"%s","query":{"from":"2026-01-01","to":"2026-03-31",
                "granularity":"WEEK","projectIds":[],"teamIds":[],"employeeIds":[]},"sprintId":null}
                """.formatted(type, format);
    }

    @Test
    void pdfDownloadReturnsValidResponseWithHeaders() throws Exception {
        when(reportService.buildReport(any())).thenReturn(reportData);
        byte[] pdf = "%PDF-1.7 mock".getBytes();
        when(pdfRenderer.render(reportData)).thenReturn(pdf);

        mockMvc.perform(post("/api/v1/reports/pdf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("EXECUTIVE_OVERVIEW", "PDF")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"taskflow-executive-overview-20261121-101500.pdf\""))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(content().bytes(pdf));

        verify(pdfRenderer).render(reportData);
        verify(excelRenderer, never()).render(any());
    }

    @Test
    void xlsxDownloadReturnsValidResponseWithHeaders() throws Exception {
        when(reportService.buildReport(any())).thenReturn(reportData);
        byte[] xlsx = "XLSX mock".getBytes();
        when(excelRenderer.render(reportData)).thenReturn(xlsx);

        mockMvc.perform(post("/api/v1/reports/xlsx")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("EXECUTIVE_OVERVIEW", "XLSX")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"taskflow-executive-overview-20261121-101500.xlsx\""))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(content().bytes(xlsx));

        verify(excelRenderer).render(reportData);
        verify(pdfRenderer, never()).render(any());
    }

    @Test
    void catalogReturnsAllTypesAndFormats() throws Exception {
        mockMvc.perform(get("/api/v1/reports/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportTypes.length()").value(10))
                .andExpect(jsonPath("$.formats.length()").value(2))
                .andExpect(jsonPath("$.formats[?(@.id=='PDF')]").exists())
                .andExpect(jsonPath("$.formats[?(@.id=='XLSX')]").exists())
                .andExpect(jsonPath("$.reportTypes[?(@.id=='SPRINT_BURNOWN')].requiresSprintId")
                        .value(true))
                .andExpect(jsonPath("$.reportTypes[?(@.id=='EXECUTIVE_OVERVIEW')].requiresSprintId")
                        .value(false));
    }

    @Test
    void invalidRequestReturnsValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/reports/pdf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"format":"PDF","query":{"from":"2026-01-01","to":"2026-03-31",
                                "granularity":"WEEK","projectIds":[],"teamIds":[],"employeeIds":[]}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Request validation failed."));
    }

    @Test
    void invalidReportTypeOrFormatReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/reports/pdf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("NOT_A_REPORT", "PDF")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MALFORMED_REQUEST"));

        mockMvc.perform(post("/api/v1/reports/xlsx")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("EXECUTIVE_OVERVIEW", "NOT_A_FORMAT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MALFORMED_REQUEST"));
    }

    @Test
    void invalidDateRangeReturnsValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/reports/pdf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"EXECUTIVE_OVERVIEW","format":"PDF",
                                "query":{"from":"2026-04-01","to":"2026-03-31",
                                "granularity":"WEEK","projectIds":[],"teamIds":[],"employeeIds":[]}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void sprintBurndownWithoutSprintIdReturnsValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/reports/pdf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("SPRINT_BURNOWN", "PDF")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void businessRuleFailureReturnsUnprocessableEntity() throws Exception {
        when(reportService.buildReport(any())).thenThrow(new BusinessRuleException("bad request"));

        mockMvc.perform(post("/api/v1/reports/pdf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("EXECUTIVE_OVERVIEW", "PDF")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void authorizationFailureReturnsForbidden() throws Exception {
        when(reportService.buildReport(any()))
                .thenThrow(new UnauthorizedResourceAccessException("not allowed"));

        mockMvc.perform(post("/api/v1/reports/pdf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("EXECUTIVE_OVERVIEW", "PDF")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void missingSprintReturnsNotFound() throws Exception {
        when(reportService.buildReport(any()))
                .thenThrow(new ResourceNotFoundException("Sprint not found: 7"));

        String withSprint = """
                {"type":"SPRINT_BURNOWN","format":"PDF",
                "query":{"from":"2026-01-01","to":"2026-03-31","granularity":"WEEK",
                "projectIds":[],"teamIds":[],"employeeIds":[]},"sprintId":7}
                """;
        mockMvc.perform(post("/api/v1/reports/pdf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(withSprint))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void rendererFailureReturnsSafeInternalError() throws Exception {
        when(reportService.buildReport(any())).thenReturn(reportData);
        when(pdfRenderer.render(reportData)).thenThrow(new IllegalStateException("internal detail"));

        mockMvc.perform(post("/api/v1/reports/pdf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("EXECUTIVE_OVERVIEW", "PDF")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected server error occurred."));
    }

    @Test
    void filenameFactoryProducesSanitizedName() {
        String filename = com.taskflow.util.report.ReportFilenameFactory.create(
                ReportType.EXECUTIVE_OVERVIEW, com.taskflow.dto.report.ReportFormat.PDF,
                Instant.parse("2026-11-21T10:15:00Z"));
        assertThat(filename).isEqualTo("taskflow-executive-overview-20261121-101500.pdf");
    }

    @Test
    void controllerDoesNotExposeReportDataAsJson() throws Exception {
        when(reportService.buildReport(any())).thenReturn(reportData);
        when(pdfRenderer.render(reportData)).thenReturn(new byte[]{1, 2, 3});

        String body = mockMvc.perform(post("/api/v1/reports/pdf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("EXECUTIVE_OVERVIEW", "PDF")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("reportTitle", "organizationName", "summaryKpis");
    }
}
