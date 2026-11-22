package com.taskflow.util.report;

import com.taskflow.dto.report.ReportFormat;
import com.taskflow.dto.report.ReportType;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ReportFilenameFactoryTest {

    @Test
    void filenamesAreDeterministicAndUseUtcTimestamp() {
        Instant instant = Instant.parse("2026-11-20T13:45:00Z");
        assertThat(ReportFilenameFactory.create(ReportType.EXECUTIVE_OVERVIEW, ReportFormat.PDF, instant))
                .isEqualTo("taskflow-executive-overview-20261120-134500.pdf");
        assertThat(ReportFilenameFactory.create(ReportType.QA_ANALYTICS, ReportFormat.XLSX, instant))
                .isEqualTo("taskflow-qa-analytics-20261120-134500.xlsx");
    }

    @Test
    void sanitizeRemovesPathsAndUnsafeCharacters() {
        assertThat(ReportFilenameFactory.sanitize("../../etc/passwd")).isEqualTo("passwd");
        assertThat(ReportFilenameFactory.sanitize("C:\\temp\\My Report!!.pdf"))
                .isEqualTo("my-report-pdf");
        assertThat(ReportFilenameFactory.sanitize("  UPPER__CASE  ")).isEqualTo("upper-case");
    }

    @Test
    void sanitizeFallsBackForBlankOrPunctuationOnlyInput() {
        assertThat(ReportFilenameFactory.sanitize(null)).isEqualTo("report");
        assertThat(ReportFilenameFactory.sanitize("   ")).isEqualTo("report");
        assertThat(ReportFilenameFactory.sanitize("../..")).isEqualTo("report");
    }

    @Test
    void sanitizeCapsLengthAndTrimsTrailingDashes() {
        String longValue = "a".repeat(200) + "-";
        String result = ReportFilenameFactory.sanitize(longValue);
        assertThat(result).hasSizeLessThanOrEqualTo(80);
        assertThat(result).doesNotEndWith("-");
    }
}
