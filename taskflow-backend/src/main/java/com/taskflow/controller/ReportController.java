package com.taskflow.controller;

import com.taskflow.dto.report.AnalyticsReportRequest;
import com.taskflow.dto.report.ReportCatalogResponse;
import com.taskflow.dto.report.ReportFormat;
import com.taskflow.dto.report.ReportType;
import com.taskflow.dto.report.model.AnalyticsReportData;
import com.taskflow.service.AnalyticsReportService;
import com.taskflow.service.report.ExcelReportRenderer;
import com.taskflow.service.report.PdfReportRenderer;
import com.taskflow.util.report.ReportFilenameFactory;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/** Secure, thin REST layer for PDF/XLSX analytics report downloads. */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private static final List<ReportCatalogResponse.ReportFormatEntry> FORMAT_ENTRIES = Arrays.stream(ReportFormat.values())
            .map(format -> new ReportCatalogResponse.ReportFormatEntry(
                    format.name(), format.name(), format.contentType(), format.extension()))
            .toList();
    private static final List<String> ALL_FORMATS = Arrays.stream(ReportFormat.values())
            .map(Enum::name).toList();

    private final AnalyticsReportService reportService;
    private final PdfReportRenderer pdfRenderer;
    private final ExcelReportRenderer excelRenderer;

    public ReportController(AnalyticsReportService reportService,
                            PdfReportRenderer pdfRenderer,
                            ExcelReportRenderer excelRenderer) {
        this.reportService = reportService;
        this.pdfRenderer = pdfRenderer;
        this.excelRenderer = excelRenderer;
    }

    @PostMapping("/pdf")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> generatePdf(@Valid @RequestBody AnalyticsReportRequest request) {
        return download(reportService.buildReport(request), ReportFormat.PDF);
    }

    @PostMapping("/xlsx")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> generateXlsx(@Valid @RequestBody AnalyticsReportRequest request) {
        return download(reportService.buildReport(request), ReportFormat.XLSX);
    }

    @GetMapping("/catalog")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ReportCatalogResponse> catalog() {
        List<ReportCatalogResponse.ReportTypeEntry> types = Arrays.stream(ReportType.values())
                .map(type -> new ReportCatalogResponse.ReportTypeEntry(
                        type.name(),
                        type.label(),
                        ALL_FORMATS,
                        type == ReportType.SPRINT_BURNOWN))
                .toList();
        return ResponseEntity.ok(new ReportCatalogResponse(types, FORMAT_ENTRIES));
    }

    private ResponseEntity<byte[]> download(AnalyticsReportData data, ReportFormat format) {
        byte[] bytes = format == ReportFormat.PDF
                ? pdfRenderer.render(data)
                : excelRenderer.render(data);
        Instant generatedAt = data.metadata().generatedAt() == null
                ? Instant.now() : data.metadata().generatedAt();
        String filename = ReportFilenameFactory.create(data.reportType(), format, generatedAt);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(format.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(bytes);
    }
}
