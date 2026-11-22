package com.taskflow.service.report.impl;

import com.taskflow.config.ReportProperties;
import com.taskflow.dto.analytics.AnalyticsGranularity;
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
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PoiExcelReportRendererTest {

    private final PoiExcelReportRenderer renderer =
            new PoiExcelReportRenderer(new ReportProperties(120, 20000, 50, 50));

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

    private static XSSFWorkbook open(byte[] bytes) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(bytes));
    }

    private static String allCellText(XSSFSheet sheet) {
        StringBuilder text = new StringBuilder();
        DataFormatter formatter = new DataFormatter();
        for (Row row : sheet) {
            for (Cell cell : row) {
                text.append(formatter.formatCellValue(cell)).append('\n');
            }
        }
        return text.toString();
    }

    private static Cell findCell(XSSFSheet sheet, String value) {
        DataFormatter formatter = new DataFormatter();
        for (Row row : sheet) {
            for (Cell cell : row) {
                if (value.equals(formatter.formatCellValue(cell))) {
                    return cell;
                }
            }
        }
        return null;
    }

    @Test
    void generatesValidXlsxThatCanBeReopened() throws Exception {
        byte[] bytes = renderer.render(model(ReportType.EXECUTIVE_OVERVIEW, List.of(), List.of(), List.of(), false));
        assertThat(bytes).isNotEmpty();
        try (XSSFWorkbook workbook = open(bytes)) {
            assertThat(workbook.getNumberOfSheets()).isGreaterThanOrEqualTo(1);
            assertThat(workbook.getSheet("Executive Summary")).isNotNull();
        }
    }

    @ParameterizedTest
    @EnumSource(ReportType.class)
    void rendersAllTenReportTypes(ReportType type) throws Exception {
        ReportSection section = new ReportSection("Analytics", "Real data only",
                List.of(), List.of(table(2)),
                List.of(new ReportChart(type.label() + " Chart", ChartType.BAR,
                        List.of(series("Value", 3)), List.of())), List.of());
        byte[] bytes = renderer.render(model(type, List.of(), List.of(section), List.of(), false));

        try (XSSFWorkbook workbook = open(bytes)) {
            assertThat(workbook.getNumberOfSheets()).isGreaterThanOrEqualTo(2);
            assertThat(allCellText(workbook.getSheet("Executive Summary"))).contains(type.label());
            XSSFSheet sectionSheet = workbook.getSheetAt(1);
            assertThat(allCellText(sectionSheet)).contains("Analytics", "Task 1", "Value");
        }
    }

    @Test
    void executiveSummaryContainsOrganizationPeriodAndFilters() throws Exception {
        byte[] bytes = renderer.render(model(ReportType.PROJECT_PERFORMANCE, List.of(), List.of(), List.of(), false));
        try (XSSFWorkbook workbook = open(bytes)) {
            String content = allCellText(workbook.getSheet("Executive Summary"));
            assertThat(content).contains("TaskFlow Analytics Report", "TaskFlow Enterprise",
                    "Project Performance", "2026-11-21 10:15 UTC", "2026-01-01 to 2026-03-31",
                    "Granularity: WEEK", "Projects: 1 selected");
        }
    }

    @Test
    void worksheetNamesAreValidUniqueAndWithinLimit() throws Exception {
        ReportSection one = new ReportSection("Very Long Section Title ".repeat(4), "", List.of(),
                List.of(table(1)), List.of(), List.of());
        ReportSection two = new ReportSection("Very Long Section Title ".repeat(4), "", List.of(),
                List.of(table(1)), List.of(), List.of());
        byte[] bytes = renderer.render(model(ReportType.TEAM_PRODUCTIVITY, List.of(),
                List.of(one, two), List.of(), false));
        try (XSSFWorkbook workbook = open(bytes)) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(3);
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                String name = workbook.getSheetName(i);
                assertThat(name).hasSizeLessThanOrEqualTo(31);
                assertThat(name).doesNotContain("[", "]", ":", "*", "?", "/", "\\");
            }
            assertThat(workbook.getSheetName(1)).isNotEqualTo(workbook.getSheetName(2));
        }
    }

    @Test
    void kpisPreserveZeroNullComparisonAndAvailability() throws Exception {
        List<ReportKpi> kpis = List.of(
                new ReportKpi("zero", "True Zero", MetricValue.available(BigDecimal.ZERO, "count"), null),
                new ReportKpi("missing", "Missing", MetricValue.noData("count"), null),
                new ReportKpi("compared", "Compared",
                        MetricValue.compared(BigDecimal.valueOf(4), BigDecimal.valueOf(2), "count"), null));
        byte[] bytes = renderer.render(model(ReportType.EXECUTIVE_OVERVIEW, kpis, List.of(), List.of(), false));

        try (XSSFWorkbook workbook = open(bytes)) {
            XSSFSheet sheet = workbook.getSheet("Executive Summary");
            Cell zero = findCell(sheet, "True Zero");
            assertThat(zero).isNotNull();
            Row zeroRow = zero.getRow();
            assertThat(zeroRow.getCell(1).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(zeroRow.getCell(1).getNumericCellValue()).isEqualTo(0d);
            assertThat(allCellText(sheet)).contains("No data in range", "Available");

            Cell compared = findCell(sheet, "Compared");
            assertThat(compared).isNotNull();
            assertThat(compared.getRow().getCell(3).getNumericCellValue()).isEqualTo(100d);
            assertThat(allCellText(sheet)).contains("UP");
        }
    }

    @Test
    void dateAndPercentageFormatsAreApplied() throws Exception {
        byte[] bytes = renderer.render(model(ReportType.BUG_TRENDS, List.of(
                        new ReportKpi("rate", "Rate", MetricValue.available(new BigDecimal("33.33"), "%"), null)),
                List.of(), List.of(), false));
        try (XSSFWorkbook workbook = open(bytes)) {
            XSSFSheet sheet = workbook.getSheet("Executive Summary");
            Cell rate = findCell(sheet, "Rate");
            assertThat(rate).isNotNull();
            assertThat(rate.getRow().getCell(1).getCellStyle().getDataFormatString()).contains("%");
            assertThat(findCell(sheet, "2026-11-21 10:15 UTC")).isNotNull();
            assertThat(findCell(sheet, "2026-01-01 to 2026-03-31")).isNotNull();
        }
    }

    @Test
    void freezePanesAndAutoFiltersAreConfigured() throws Exception {
        ReportSection section = new ReportSection("Projects", "", List.of(), List.of(table(5)), List.of(), List.of());
        byte[] bytes = renderer.render(model(ReportType.PROJECT_PERFORMANCE, List.of(), List.of(section), List.of(), false));
        try (XSSFWorkbook workbook = open(bytes)) {
            assertThat(workbook.getSheet("Executive Summary").getPaneInformation()).isNotNull();
            XSSFSheet sectionSheet = workbook.getSheetAt(1);
            assertThat(sectionSheet.getPaneInformation()).isNotNull();
            assertThat(sectionSheet.getCTWorksheet().getAutoFilter()).isNotNull();
            assertThat(sectionSheet.getCTWorksheet().getAutoFilter().getRef()).isNotBlank();
        }
    }

    @ParameterizedTest
    @EnumSource(ChartType.class)
    void chartTypesProduceNativeChartsOrPreservedTables(ChartType chartType) throws Exception {
        ReportSection section = new ReportSection("Charts", "", List.of(), List.of(),
                List.of(new ReportChart(chartType + " chart", chartType,
                        List.of(series("Created", 6), series("Resolved", 6)), List.of())), List.of());
        byte[] bytes = renderer.render(model(ReportType.BUG_TRENDS, List.of(), List.of(section), List.of(), false));

        try (XSSFWorkbook workbook = open(bytes)) {
            XSSFSheet sheet = workbook.getSheetAt(1);
            XSSFDrawing drawing = sheet.getDrawingPatriarch();
            if (chartType == ChartType.TABLE) {
                assertThat(drawing == null || drawing.getCharts().isEmpty()).isTrue();
                assertThat(allCellText(sheet)).contains("D1", "Created", "Resolved");
            } else {
                assertThat(drawing).isNotNull();
                assertThat(drawing.getCharts()).isNotEmpty();
                assertThat(allCellText(sheet)).contains("D1");
            }
        }
    }

    @Test
    void emptyDatasetRendersExplanation() throws Exception {
        byte[] bytes = renderer.render(model(ReportType.QA_ANALYTICS, List.of(), List.of(),
                List.of("No data in range"), true));
        try (XSSFWorkbook workbook = open(bytes)) {
            String content = allCellText(workbook.getSheet("Executive Summary"));
            assertThat(content).contains("No data is available", "not converted to zero", "No data in range");
        }
    }

    @Test
    void largeTableIsTruncatedByRowLimit() throws Exception {
        PoiExcelReportRenderer limited = new PoiExcelReportRenderer(new ReportProperties(120, 10, 50, 50));
        ReportSection section = new ReportSection("Projects", "", List.of(), List.of(table(200)), List.of(), List.of());
        byte[] bytes = limited.render(model(ReportType.PROJECT_PERFORMANCE, List.of(), List.of(section), List.of(), false));
        try (XSSFWorkbook workbook = open(bytes)) {
            String content = allCellText(workbook.getSheetAt(1));
            assertThat(content).contains("Table truncated at ", " of 200 rows");
            assertThat(workbook.getSheetAt(1).getLastRowNum()).isLessThan(60);
        }
    }

    @Test
    void bucketLimitIsEnforced() throws Exception {
        PoiExcelReportRenderer limited = new PoiExcelReportRenderer(new ReportProperties(3, 20000, 50, 50));
        ReportSection section = new ReportSection("Trends", "", List.of(), List.of(),
                List.of(new ReportChart("Created", ChartType.LINE, List.of(series("Created", 10)), List.of())), List.of());
        byte[] bytes = limited.render(model(ReportType.BUG_TRENDS, List.of(), List.of(section), List.of(), false));
        try (XSSFWorkbook workbook = open(bytes)) {
            assertThat(allCellText(workbook.getSheetAt(1))).contains("Chart truncated to 3 buckets");
        }
    }

    @Test
    void kpiLimitIsEnforced() throws Exception {
        PoiExcelReportRenderer limited = new PoiExcelReportRenderer(new ReportProperties(120, 20000, 2, 50));
        List<ReportKpi> kpis = IntStream.range(0, 5)
                .mapToObj(i -> new ReportKpi("kpi" + i, "KPI " + i,
                        MetricValue.available(BigDecimal.valueOf(i), "count"), null))
                .toList();
        byte[] bytes = limited.render(model(ReportType.EXECUTIVE_OVERVIEW, kpis, List.of(), List.of(), false));
        try (XSSFWorkbook workbook = open(bytes)) {
            assertThat(allCellText(workbook.getSheet("Executive Summary")))
                    .contains("KPI list truncated to 2 of 5");
        }
    }

    @Test
    void longTextIsWrittenWithoutFailure() throws Exception {
        ReportMetadata longMetadata = new ReportMetadata(
                "TaskFlow Enterprise " + "Global Division ".repeat(20), "taskflow-enterprise",
                "Software", "UTC", "Very Long Report Title " + "For Analytics ".repeat(20),
                Instant.now(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31),
                AnalyticsGranularity.WEEK, false, "basis", List.of(), Map.of());
        byte[] bytes = renderer.render(new AnalyticsReportData(longMetadata, ReportType.PROJECT_GROWTH,
                List.of(), List.of(), List.of(), false));
        try (XSSFWorkbook workbook = open(bytes)) {
            assertThat(allCellText(workbook.getSheet("Executive Summary")))
                    .contains("TaskFlow Enterprise", "Very Long Report Title");
        }
    }

    @Test
    void repeatedRendersReleaseResources() throws Exception {
        for (int i = 0; i < 8; i++) {
            byte[] bytes = renderer.render(model(ReportType.QA_ANALYTICS, List.of(
                    new ReportKpi("pass", "Passed", MetricValue.available(BigDecimal.valueOf(i), "count"), null)),
                    List.of(), List.of(), false));
            try (XSSFWorkbook workbook = open(bytes)) {
                assertThat(workbook.getNumberOfSheets()).isGreaterThanOrEqualTo(1);
            }
        }
    }

    @Test
    void nullReportDataIsRejected() {
        assertThatThrownBy(() -> renderer.render(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sectionSheetsUseCleanNamesWithoutNumericSuffix() throws Exception {
        ReportSection one = new ReportSection("Project Status", "", List.of(), List.of(table(1)), List.of(), List.of());
        ReportSection two = new ReportSection("Quality Analytics", "", List.of(), List.of(table(1)), List.of(), List.of());
        byte[] bytes = renderer.render(model(ReportType.EXECUTIVE_OVERVIEW, List.of(),
                List.of(one, two), List.of(), false));
        try (XSSFWorkbook workbook = open(bytes)) {
            assertThat(workbook.getSheetName(1)).isEqualTo("Project Status");
            assertThat(workbook.getSheetName(2)).isEqualTo("Quality Analytics");
        }
    }

    @Test
    void duplicateSectionTitlesAreDisambiguatedWithSuffix() throws Exception {
        ReportSection one = new ReportSection("Analytics", "", List.of(), List.of(table(1)), List.of(), List.of());
        ReportSection two = new ReportSection("Analytics", "", List.of(), List.of(table(1)), List.of(), List.of());
        byte[] bytes = renderer.render(model(ReportType.EXECUTIVE_OVERVIEW, List.of(),
                List.of(one, two), List.of(), false));
        try (XSSFWorkbook workbook = open(bytes)) {
            assertThat(workbook.getSheetName(1)).isEqualTo("Analytics");
            assertThat(workbook.getSheetName(2)).isEqualTo("Analytics-2");
        }
    }

    @Test
    void summaryWorksheetCellsLinkToTheirSections() throws Exception {
        ReportSection section = new ReportSection("Analytics", "", List.of(), List.of(table(2)), List.of(), List.of());
        byte[] bytes = renderer.render(model(ReportType.EXECUTIVE_OVERVIEW, List.of(),
                List.of(section), List.of(), false));
        try (XSSFWorkbook workbook = open(bytes)) {
            XSSFSheet summary = workbook.getSheet("Executive Summary");
            boolean found = false;
            for (Row row : summary) {
                Cell cell = row.getCell(1);
                if (cell != null && cell.getCellType() == CellType.STRING
                        && "Analytics".equals(cell.getStringCellValue())) {
                    assertThat(cell.getHyperlink()).isNotNull();
                    assertThat(cell.getHyperlink().getAddress()).contains("'Analytics'!A1");
                    found = true;
                }
            }
            assertThat(found).as("navigation hyperlink to Analytics sheet").isTrue();
        }
    }

    @Test
    void summaryKpiValuesAreHighlighted() throws Exception {
        byte[] bytes = renderer.render(model(ReportType.EXECUTIVE_OVERVIEW,
                List.of(new ReportKpi("k", "Highlighted KPI",
                        MetricValue.available(BigDecimal.ONE, "count"), null)),
                List.of(), List.of(), false));
        try (XSSFWorkbook workbook = open(bytes)) {
            Cell kpi = findCell(workbook.getSheet("Executive Summary"), "Highlighted KPI");
            assertThat(kpi).isNotNull();
            assertThat(kpi.getRow().getCell(1).getCellStyle().getFillForegroundColor())
                    .isEqualTo(IndexedColors.LIGHT_GREEN.getIndex());
        }
    }

    @Test
    void localDateCellsRetainDateTypeAndFormat() throws Exception {
        ReportTable dates = new ReportTable("Dates", List.of("Date", "Count"),
                List.of(List.of(LocalDate.of(2026, 1, 5), 3)));
        ReportSection section = new ReportSection("Dates", "", List.of(),
                List.of(dates), List.of(), List.of());
        byte[] bytes = renderer.render(model(ReportType.PROJECT_GROWTH, List.of(),
                List.of(section), List.of(), false));
        try (XSSFWorkbook workbook = open(bytes)) {
            XSSFSheet sheet = workbook.getSheet("Dates");
            Cell header = findCell(sheet, "Date");
            assertThat(header).isNotNull();
            Cell date = sheet.getRow(header.getRowIndex() + 1).getCell(0);
            assertThat(date.getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(date.getCellStyle().getDataFormatString()).isEqualTo("yyyy-mm-dd");
        }
    }

    @Test
    void longSectionDescriptionsRemainFullyAccessible() throws Exception {
        String longText = "Long description " + "with many words ".repeat(20);
        ReportSection section = new ReportSection("Details", longText, List.of(),
                List.of(table(1)), List.of(), List.of());
        byte[] bytes = renderer.render(model(ReportType.EXECUTIVE_OVERVIEW, List.of(),
                List.of(section), List.of(), false));
        try (XSSFWorkbook workbook = open(bytes)) {
            assertThat(allCellText(workbook.getSheet("Details"))).contains(longText.trim());
        }
    }

    @Test
    void executiveInsightsDistinguishFactsFromRecommendations() throws Exception {
        ReportTable insights = new ReportTable("Executive Insights",
                List.of("Area", "Measured fact", "Recommendation"),
                List.of(List.of("Projects", "Total 2, active 1.", "Review status.")));
        ReportSection section = new ReportSection("Executive Insights", "", List.of(),
                List.of(insights), List.of(), List.of());
        byte[] bytes = renderer.render(model(ReportType.EXECUTIVE_OVERVIEW, List.of(),
                List.of(section), List.of(), false));
        try (XSSFWorkbook workbook = open(bytes)) {
            XSSFSheet sheet = workbook.getSheet("Executive Insights");
            Cell area = findCell(sheet, "Projects");
            assertThat(area).isNotNull();
            assertThat(area.getRow().getCell(0).getCellStyle().getFontIndex())
                    .isNotEqualTo(area.getRow().getCell(2).getCellStyle().getFontIndex());
        }
    }
}
