package com.taskflow.dto.report;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReportTypeAndFormatTest {

    @Test
    void allTenAnalyticsReportTypesAreDefined() {
        assertThat(ReportType.values()).hasSize(10);
        assertThat(ReportType.EXECUTIVE_OVERVIEW.label()).isEqualTo("Executive Overview");
        assertThat(ReportType.QA_ANALYTICS.label()).isEqualTo("QA Analytics");
        assertThat(ReportType.SPRINT_BURNOWN.label()).isEqualTo("Sprint Burndown");
    }

    @Test
    void formatsExposeSafeExtensionsAndContentTypes() {
        assertThat(ReportFormat.PDF.extension()).isEqualTo("pdf");
        assertThat(ReportFormat.PDF.contentType()).isEqualTo("application/pdf");
        assertThat(ReportFormat.XLSX.extension()).isEqualTo("xlsx");
        assertThat(ReportFormat.XLSX.contentType())
                .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }
}
