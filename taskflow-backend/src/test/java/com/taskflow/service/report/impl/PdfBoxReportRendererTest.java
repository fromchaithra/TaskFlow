package com.taskflow.service.report.impl;

import com.taskflow.config.ReportProperties;
import com.taskflow.dto.analytics.AnalyticsGranularity;
import com.taskflow.dto.analytics.DataAvailability;
import com.taskflow.dto.analytics.MetricValue;
import com.taskflow.dto.report.ReportMetadata;
import com.taskflow.dto.report.ReportType;
import com.taskflow.dto.report.model.AnalyticsReportData;
import com.taskflow.dto.report.model.ChartSeries;
import com.taskflow.dto.report.model.ChartType;
import com.taskflow.dto.report.model.ChartValue;
import com.taskflow.dto.report.model.ReportChart;
import com.taskflow.dto.report.model.ReportKpi;
import com.taskflow.dto.report.model.ReportSection;
import com.taskflow.dto.report.model.ReportTable;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdfBoxReportRendererTest {

    private final PdfBoxReportRenderer renderer =
            new PdfBoxReportRenderer(new ReportProperties(120, 20000, 50, 50));

    private static ReportMetadata metadata(ReportType type) {
        return new ReportMetadata(
                "TaskFlow Enterprise", "taskflow-enterprise", "Software", "UTC",
                type.label(), Instant.parse("2026-11-21T10:15:00Z"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                AnalyticsGranularity.WEEK, false, "unit test basis",
                List.of("Granularity: WEEK", "Projects: 1 selected"), Map.of());
    }

    private static AnalyticsReportData model(ReportType type, List<ReportKpi> kpis,
                                             List<ReportSection> sections, List<String> notes, boolean empty) {
        return new AnalyticsReportData(metadata(type), type, kpis, sections, notes, empty);
    }

    private static ChartSeries series(String name, int count) {
        return new ChartSeries(name, IntStream.range(0, count)
                .mapToObj(i -> new ChartValue("D" + (i + 1),
                        MetricValue.available(BigDecimal.valueOf(i + 1), "count")))
                .toList());
    }

    private static ReportTable table(int rows) {
        return new ReportTable("Tasks", List.of("Task", "Count", "Metric"),
                IntStream.range(0, rows)
                        .mapToObj(i -> List.<Object>of("Task " + (i + 1), i + 1,
                                MetricValue.available(BigDecimal.valueOf(i + 1), "count")))
                        .toList());
    }

    private static String text(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private static int pages(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return document.getNumberOfPages();
        }
    }

    @Test
    void generatesValidPdfWithHeaderAndFooterStructure() throws Exception {
        byte[] pdf = renderer.render(model(ReportType.EXECUTIVE_OVERVIEW,
                List.of(new ReportKpi("totalTasks", "Total Tasks",
                        MetricValue.available(BigDecimal.valueOf(4), "count"), null)),
                List.of(), List.of(), false));

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(pages(pdf)).isGreaterThanOrEqualTo(1);
        String content = text(pdf);
        assertThat(content).contains("TaskFlow", "TaskFlow Enterprise", "Executive Overview",
                "Generated:", "Period:", "Granularity: WEEK", "Page 1", "Confidential");
    }

    @ParameterizedTest
    @EnumSource(ReportType.class)
    void rendersAllTenReportTypes(ReportType type) throws Exception {
        ReportSection section = new ReportSection("Analytics", "Real data only",
                List.of(), List.of(table(2)),
                List.of(new ReportChart(type.label() + " Chart", ChartType.BAR,
                        List.of(series("Value", 3)), List.of())),
                List.of());
        byte[] pdf = renderer.render(model(type, List.of(), List.of(section), List.of(), false));

        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(text(pdf)).contains(type.label(), "Analytics", "Task", "Value");
    }

    @Test
    void rendersKpisZeroNullAndComparison() throws Exception {
        byte[] pdf = renderer.render(model(ReportType.EXECUTIVE_OVERVIEW, List.of(
                        new ReportKpi("zero", "True Zero",
                                MetricValue.available(BigDecimal.ZERO, "count"), null),
                        new ReportKpi("missing", "Missing", MetricValue.noData("count"), null),
                        new ReportKpi("compared", "Compared",
                                MetricValue.compared(BigDecimal.valueOf(4), BigDecimal.valueOf(2), "count"), null)),
                List.of(), List.of(), false));

        String content = text(pdf);
        assertThat(content).contains("True Zero", "Missing", "Compared");
        assertThat(content).contains("0", "No data in range", "100%", "UP");
    }

    @Test
    void rendersTablesWithRepeatedHeadersAcrossPages() throws Exception {
        byte[] pdf = renderer.render(model(ReportType.PROJECT_PERFORMANCE, List.of(),
                List.of(new ReportSection("Projects", "Large table", List.of(),
                        List.of(table(90)), List.of(), List.of())), List.of(), false));

        assertThat(pages(pdf)).isGreaterThan(1);
        String content = text(pdf);
        int headerCount = content.split("Task", -1).length - 1;
        assertThat(headerCount).isGreaterThan(1);
        assertThat(content).contains("Task 1", "Task 90");
    }

    @ParameterizedTest
    @EnumSource(ChartType.class)
    void supportsEveryChartType(ChartType chartType) throws Exception {
        byte[] pdf = renderer.render(model(ReportType.BUG_TRENDS, List.of(),
                List.of(new ReportSection("Charts", "Chart type " + chartType, List.of(), List.of(),
                        List.of(new ReportChart(chartType + " chart", chartType,
                                List.of(series("Created", 6), series("Resolved", 6)), List.of())),
                        List.of())), List.of(), false));

        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(text(pdf)).contains(chartType + " chart", "D1");
    }

    @Test
    void emptyDatasetRendersExplanationInsteadOfFabricatedZeros() throws Exception {
        byte[] pdf = renderer.render(model(ReportType.QA_ANALYTICS, List.of(), List.of(),
                List.of("No data in range"), true));

        assertThat(text(pdf)).contains("No data is available", "substituted", "No data in range");
    }

    @Test
    void availabilityNotesAreRendered() throws Exception {
        byte[] pdf = renderer.render(model(ReportType.EMPLOYEE_WORKLOAD, List.of(),
                List.of(new ReportSection("Workload", "", List.of(), List.of(), List.of(),
                        List.of("Not configured"))),
                List.of("Insufficient history"), false));

        String content = text(pdf);
        assertThat(content).contains("Insufficient history", "Availability: Not configured");
    }

    @Test
    void longOrganizationNameAndTitleAreWrappedWithoutClipping() throws Exception {
        ReportMetadata longMetadata = new ReportMetadata(
                "TaskFlow Enterprise " + "Global Division ".repeat(12),
                "taskflow-enterprise", "Software", "UTC",
                "Very Long Report Title " + "For Analytics ".repeat(12),
                Instant.now(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                AnalyticsGranularity.WEEK, false, "basis", List.of(), Map.of());
        byte[] pdf = renderer.render(new AnalyticsReportData(longMetadata, ReportType.PROJECT_GROWTH,
                List.of(), List.of(), List.of(), false));

        assertThat(pages(pdf)).isGreaterThanOrEqualTo(1);
        assertThat(text(pdf)).contains("TaskFlow", "Global Division", "Very Long Report Title");
    }

    @Test
    void maximumPageLimitIsEnforced() throws Exception {
        PdfBoxReportRenderer limited = new PdfBoxReportRenderer(new ReportProperties(120, 20000, 50, 1));
        byte[] pdf = limited.render(model(ReportType.PROJECT_PERFORMANCE, List.of(),
                List.of(new ReportSection("Projects", "", List.of(), List.of(table(200)), List.of(), List.of())),
                List.of(), false));

        assertThat(pages(pdf)).isEqualTo(1);
        assertThat(text(pdf)).contains("maximum page limit reached");
    }

    @Test
    void maximumKpiLimitIsEnforced() throws Exception {
        PdfBoxReportRenderer limited = new PdfBoxReportRenderer(new ReportProperties(120, 20000, 2, 50));
        List<ReportKpi> kpis = IntStream.range(0, 5)
                .mapToObj(i -> new ReportKpi("kpi" + i, "KPI " + i,
                        MetricValue.available(BigDecimal.valueOf(i), "count"), null))
                .toList();
        byte[] pdf = limited.render(model(ReportType.EXECUTIVE_OVERVIEW, kpis, List.of(), List.of(), false));

        assertThat(text(pdf)).contains("KPI list truncated to 2 of 5");
    }

    @Test
    void maximumBucketLimitIsEnforced() throws Exception {
        PdfBoxReportRenderer limited = new PdfBoxReportRenderer(new ReportProperties(3, 20000, 50, 50));
        byte[] pdf = limited.render(model(ReportType.BUG_TRENDS, List.of(),
                List.of(new ReportSection("Trends", "", List.of(), List.of(),
                        List.of(new ReportChart("Created", ChartType.LINE,
                                List.of(series("Created", 10)), List.of())), List.of())), List.of(), false));

        assertThat(text(pdf)).contains("Chart truncated to 3 buckets");
    }

    @Test
    void repeatedRendersReleaseResourcesAndRemainValid() throws Exception {
        for (int i = 0; i < 8; i++) {
            byte[] pdf = renderer.render(model(ReportType.QA_ANALYTICS, List.of(
                    new ReportKpi("pass", "Passed", MetricValue.available(BigDecimal.valueOf(i), "count"), null)),
                    List.of(), List.of(), false));
            assertThat(pages(pdf)).isGreaterThanOrEqualTo(1);
        }
    }

    @Test
    void tableHeadersRepeatOnEveryPageOfALongTable() throws Exception {
        byte[] pdf = renderer.render(model(ReportType.PROJECT_PERFORMANCE, List.of(),
                List.of(new ReportSection("Projects", "Large table", List.of(),
                        List.of(table(90)), List.of(), List.of())), List.of(), false));
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
            PDFTextStripper stripper = new PDFTextStripper();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                assertThat(stripper.getText(document))
                        .as("header row on page " + page)
                        .contains("Count", "Metric");
            }
        }
    }

    @Test
    void executiveInsightsSeparateMeasuredFactsFromRecommendations() throws Exception {
        ReportTable insights = new ReportTable("Executive Insights",
                List.of("Area", "Measured fact", "Recommendation"),
                List.of(List.of("Projects", "Total 2, active 1.", "Review status.")));
        ReportSection section = new ReportSection("Executive Insights",
                "Measured facts are listed separately from conditional recommendations.",
                List.of(), List.of(insights), List.of(), List.of());
        byte[] pdf = renderer.render(model(ReportType.EXECUTIVE_OVERVIEW, List.of(),
                List.of(section), List.of(), false));

        assertThat(pages(pdf)).isGreaterThanOrEqualTo(1);
        assertThat(text(pdf)).contains("Executive Insights", "Measured fact", "Recommendation",
                "Total 2, active 1.", "Review status.");
    }

    @Test
    void nullReportDataIsRejected() {
        assertThatThrownBy(() -> renderer.render(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unavailableChartDataRendersExplanation() throws Exception {
        ReportSeriesWithNulls data = new ReportSeriesWithNulls();
        byte[] pdf = renderer.render(data.model());

        assertThat(text(pdf)).contains("No positive values are available to chart");
    }

    private static final class ReportSeriesWithNulls {
        AnalyticsReportData model() {
            ChartSeries unavailable = new ChartSeries("Unavailable", List.of(
                    new ChartValue("D1", MetricValue.noData("count")),
                    new ChartValue("D2", MetricValue.insufficientHistory("count"))));
            return PdfBoxReportRendererTest.model(ReportType.BUG_TRENDS, List.of(),
                    List.of(new ReportSection("Trends", "", List.of(), List.of(),
                            List.of(new ReportChart("Created", ChartType.BAR,
                                    List.of(unavailable), List.of(DataAvailability.NO_DATA_IN_RANGE.name()))),
                            List.of())), List.of(), false);
        }
    }
}
