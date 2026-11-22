package com.taskflow.service.report.impl;

import com.taskflow.config.ReportProperties;
import com.taskflow.dto.analytics.MetricValue;
import com.taskflow.dto.report.model.AnalyticsReportData;
import com.taskflow.dto.report.model.ChartSeries;
import com.taskflow.dto.report.model.ChartType;
import com.taskflow.dto.report.model.ChartValue;
import com.taskflow.dto.report.model.ReportChart;
import com.taskflow.dto.report.model.ReportKpi;
import com.taskflow.dto.report.model.ReportSection;
import com.taskflow.dto.report.model.ReportTable;
import com.taskflow.service.report.ExcelReportRenderer;
import com.taskflow.util.report.MetricFormatter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.ConditionalFormattingRule;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.PatternFormatting;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.SheetConditionalFormatting;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.chart.AxisPosition;
import org.apache.poi.xddf.usermodel.chart.BarDirection;
import org.apache.poi.xddf.usermodel.chart.BarGrouping;
import org.apache.poi.xddf.usermodel.chart.ChartTypes;
import org.apache.poi.xddf.usermodel.chart.LegendPosition;
import org.apache.poi.xddf.usermodel.chart.XDDFBarChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFCategoryAxis;
import org.apache.poi.xddf.usermodel.chart.XDDFCategoryDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory;
import org.apache.poi.xddf.usermodel.chart.XDDFNumericalDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFPieChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFValueAxis;
import org.apache.poi.xssf.usermodel.DefaultIndexedColorMap;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * XLSX renderer built on XSSFWorkbook.
 *
 * <p>SXSSFWorkbook is deliberately not used: it cannot create native Excel
 * charts. Memory is instead bounded by {@link ReportProperties} row, bucket
 * and KPI limits, and the workbook is streamed to a byte array.</p>
 */
@Component
public class PoiExcelReportRenderer implements ExcelReportRenderer {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);
    private static final String AVAILABLE = "AVAILABLE";
    private static final String INSIGHTS_TABLE = "Executive Insights";
    private static final int MAX_SHEET_NAME_LENGTH = 31;

    private final ReportProperties properties;

    public PoiExcelReportRenderer(ReportProperties properties) {
        this.properties = properties;
    }

    @Override
    public byte[] render(AnalyticsReportData data) {
        if (data == null) {
            throw new IllegalArgumentException("Analytics report data is required.");
        }
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Styles styles = new Styles(workbook);
            writeSummary(workbook, data, styles);
            writeSections(workbook, data, styles);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate XLSX report.", e);
        }
    }

    // ------------------------------------------------------------------
    // Executive summary
    // ------------------------------------------------------------------
    private void writeSummary(XSSFWorkbook workbook, AnalyticsReportData data, Styles styles) {
        SheetContext context = new SheetContext(workbook.createSheet("Executive Summary"),
                properties.maxRowsPerSheet());
        XSSFSheet sheet = context.sheet;
        sheet.setColumnWidth(0, 28 * 256);
        sheet.setColumnWidth(1, 20 * 256);
        sheet.setColumnWidth(2, 20 * 256);
        sheet.setColumnWidth(3, 16 * 256);
        sheet.setColumnWidth(4, 14 * 256);
        sheet.setColumnWidth(5, 24 * 256);
        sheet.setColumnWidth(6, 12 * 256);

        context.titleRow("TaskFlow Analytics Report", styles.title, 7);
        context.labelValue("Organization", safe(data.metadata().organizationName()), styles);
        context.labelValue("Report", safe(data.metadata().reportTitle()), styles);
        context.labelValue("Industry", safe(data.metadata().industry()), styles);
        context.labelValue("Timezone", safe(data.metadata().timezone()), styles);
        context.labelValue("Generated", data.metadata().generatedAt() == null ? "N/A"
                : TIMESTAMP.format(data.metadata().generatedAt()), styles);
        context.labelValue("Period", safe(String.valueOf(data.metadata().from()))
                + " to " + safe(String.valueOf(data.metadata().to())), styles);
        context.labelValue("Basis", safe(data.metadata().basis()), styles);
        for (String filter : data.metadata().appliedFilters()) {
            context.labelValue("Filter", filter, styles);
        }
        context.nextRow();

        if (data.emptyDataset()) {
            context.fullWidth("No data is available for the selected filters and date range. "
                    + "Unavailable metrics are not converted to zero.", styles.warning, 7);
            context.nextRow();
        }

        context.sectionRow("KPI Summary", styles, 7);
        writeKpiHeader(context, styles);
        List<ReportKpi> kpis = data.summaryKpis();
        int limit = properties.maxKpisPerReport();
        for (int i = 0; i < Math.min(limit, kpis.size()); i++) {
            writeKpiRow(context, kpis.get(i), styles);
        }
        if (kpis.size() > limit) {
            context.fullWidth("KPI list truncated to " + limit + " of " + kpis.size()
                    + " metrics (configured limit).", styles.warning, 7);
        }
        context.nextRow();

        context.sectionRow("Report Sections", styles, 7);
        writeRow(context, new Object[]{"Section", "Worksheet (click to navigate)", "Status"}, styles.header);
        Set<String> usedNames = new HashSet<>();
        usedNames.add("Executive Summary");
        for (ReportSection section : data.sections()) {
            boolean hasContent = !section.kpis().isEmpty() || !section.tables().isEmpty()
                    || !section.charts().isEmpty() || !section.availabilityNotes().isEmpty();
            String name = hasContent ? uniqueSheetName(section.title(), usedNames) : "N/A";
            Row row = context.nextRow();
            textCell(row, 0, safe(section.title()), styles.alternateCell);
            Cell linkCell = row.createCell(1);
            linkCell.setCellValue(name);
            if (hasContent) {
                org.apache.poi.ss.usermodel.Hyperlink link =
                        workbook.getCreationHelper().createHyperlink(HyperlinkType.DOCUMENT);
                link.setAddress("'" + name + "'!A1");
                linkCell.setHyperlink(link);
                linkCell.setCellStyle(styles.link);
            } else {
                linkCell.setCellStyle(styles.mutedCell);
            }
            textCell(row, 2, hasContent ? "Data available" : "No data available", styles.alternateCell);
            context.usedDataRow();
        }
        if (!data.availabilityNotes().isEmpty()) {
            context.nextRow();
            context.sectionRow("Data Availability", styles, 7);
            for (String note : data.availabilityNotes()) {
                context.fullWidth(note, styles.muted, 7);
            }
        }
        sheet.createFreezePane(0, 1);
        sheet.setTabColor(new XSSFColor(new java.awt.Color(15, 90, 75), new DefaultIndexedColorMap()));
    }

    private void writeKpiHeader(SheetContext context, Styles styles) {
        writeRow(context, new Object[]{"Metric", "Value", "Previous", "Change %",
                "Direction", "Availability", "Unit"}, styles.header);
    }

    private void writeKpiRow(SheetContext context, ReportKpi kpi, Styles styles) {
        MetricValue metric = kpi.metric();
        Row row = context.nextRow();
        boolean available = MetricFormatter.isAvailable(metric);
        textCell(row, 0, safe(kpi.label()), available ? styles.cell : styles.mutedCell);
        if (available) {
            numberCell(row, 1, metric.value().doubleValue(), styles.kpiNumberStyle(metric.unit()), styles.cell);
        } else {
            blankCell(row, 1, styles.cell);
        }
        if (metric != null && metric.previousValue() != null) {
            numberCell(row, 2, metric.previousValue().doubleValue(),
                    styles.numberStyle(metric.unit()), styles.cell);
        } else {
            blankCell(row, 2, styles.cell);
        }
        if (metric != null && metric.deltaPercent() != null) {
            numberCell(row, 3, metric.deltaPercent().doubleValue(), styles.percent, styles.cell);
        } else {
            blankCell(row, 3, styles.cell);
        }
        textCell(row, 4, metric == null || metric.direction() == null ? "N/A" : metric.direction(), styles.cell);
        textCell(row, 5, metric == null ? MetricFormatter.UNAVAILABLE
                : MetricFormatter.availabilityLabel(metric.availability()),
                available ? styles.cell : styles.mutedCell);
        textCell(row, 6, metric == null || metric.unit() == null ? "N/A" : metric.unit(), styles.cell);
        context.usedDataRow();
    }

    // ------------------------------------------------------------------
    // Section worksheets
    // ------------------------------------------------------------------
    private void writeSections(XSSFWorkbook workbook, AnalyticsReportData data, Styles styles) {
        Set<String> usedNames = new HashSet<>();
        usedNames.add("Executive Summary");
        for (ReportSection section : data.sections()) {
            if (section.kpis().isEmpty() && section.tables().isEmpty()
                    && section.charts().isEmpty() && section.availabilityNotes().isEmpty()) {
                continue;
            }
            String name = uniqueSheetName(section.title(), usedNames);
            SheetContext context = new SheetContext(workbook.createSheet(name),
                    properties.maxRowsPerSheet());
            context.sheet.setTabColor(new XSSFColor(new java.awt.Color(15, 157, 138), new DefaultIndexedColorMap()));
            configureSectionSheet(context.sheet, section);
            context.titleRow(safe(section.title()), styles.title, 8);
            if (section.description() != null && !section.description().isBlank()) {
                context.fullWidth(section.description(), styles.muted, 8);
                context.nextRow();
            }
            for (String note : section.availabilityNotes()) {
                context.fullWidth("Availability: " + note, styles.warning, 8);
            }
            if (!section.kpis().isEmpty()) {
                context.sectionRow("KPIs", styles, 8);
                writeKpiHeader(context, styles);
                int limit = properties.maxKpisPerReport();
                for (int k = 0; k < Math.min(limit, section.kpis().size()); k++) {
                    writeKpiRow(context, section.kpis().get(k), styles);
                }
                if (section.kpis().size() > limit) {
                    context.fullWidth("KPI list truncated to " + limit + " of "
                            + section.kpis().size() + " metrics (configured limit).", styles.warning, 8);
                }
                context.nextRow();
            }
            for (ReportTable table : section.tables()) {
                writeTable(context, table, styles);
            }
            for (ReportChart chart : section.charts()) {
                writeChart(context, chart, styles);
            }
            context.sheet.createFreezePane(0, 1);
        }
    }

    private void configureSectionSheet(XSSFSheet sheet, ReportSection section) {
        int columns = Math.max(6, section.tables().stream()
                .mapToInt(t -> t.headers() == null ? 0 : t.headers().size()).max().orElse(6));
        for (int i = 0; i < Math.min(columns, 20); i++) {
            int width = i == 0 ? 30 : 18;
            for (ReportTable table : section.tables()) {
                if (table.headers() == null || i >= table.headers().size()) {
                    continue;
                }
                width = Math.max(width, Math.min(60, displayLength(table.headers().get(i)) + 6));
                List<List<Object>> rows = table.rows() == null ? List.of() : table.rows();
                for (int r = 0; r < Math.min(rows.size(), 30); r++) {
                    List<Object> values = rows.get(r);
                    if (i < values.size() && values.get(i) != null) {
                        width = Math.max(width, Math.min(60, displayLength(values.get(i)) + 6));
                    }
                }
            }
            sheet.setColumnWidth(i, width * 256);
        }
    }

    private static int displayLength(Object value) {
        if (value instanceof MetricValue metric) {
            return MetricFormatter.format(metric).length();
        }
        return String.valueOf(value).length();
    }

    private void writeTable(SheetContext context, ReportTable table, Styles styles) {
        if (table == null || table.headers() == null || table.headers().isEmpty()) {
            return;
        }
        if (!context.canUseRows(2)) {
            context.fullWidth("Row limit reached; remaining tables omitted.", styles.warning, 8);
            return;
        }
        if (table.title() != null && !table.title().isBlank()) {
            context.fullWidth(table.title(), styles.section, Math.max(6, table.headers().size() + 1));
        }
        int headerRow = context.rowIndex();
        writeRow(context, table.headers().toArray(), styles.header, table.headers().size());

        List<List<Object>> rows = table.rows() == null ? List.of() : table.rows();
        int limit = properties.maxRowsPerSheet();
        int written = 0;
        for (List<Object> values : rows) {
            if (!context.canUseRows(1) || written >= limit) {
                context.fullWidth("Table truncated at " + written + " of " + rows.size()
                        + " rows (configured limit).", styles.warning, 8);
                break;
            }
            Object[] cells = new Object[table.headers().size()];
            for (int c = 0; c < cells.length; c++) {
                cells[c] = c < values.size() ? values.get(c) : null;
            }
            writeValueRow(context, cells, styles, written, INSIGHTS_TABLE.equals(table.title()));
            written++;
        }
        if (!rows.isEmpty()) {
            context.sheet.setAutoFilter(new CellRangeAddress(headerRow,
                    Math.min(headerRow + written, context.rowIndex() - 1), 0, table.headers().size() - 1));
        }
        addAvailabilityConditionalFormatting(context.sheet, headerRow + 1,
                Math.max(headerRow + 1, context.rowIndex() - 1), table.headers().size(), styles);
        context.nextRow();
    }

    private void writeValueRow(SheetContext context, Object[] values, Styles styles, int rowNumber,
                               boolean insights) {
        Row row = context.nextRow();
        for (int i = 0; i < values.length; i++) {
            Object value = values[i];
            CellStyle textStyle = insights && i == 0 ? styles.insightArea
                    : insights && i == 2 ? styles.recommendation
                    : (rowNumber % 2 == 0 ? styles.cell : styles.alternateCell);
            if (value == null) {
                blankCell(row, i, styles.mutedCell);
            } else if (value instanceof MetricValue metric) {
                if (MetricFormatter.isAvailable(metric)) {
                    numberCell(row, i, metric.value().doubleValue(),
                            styles.numberStyle(metric.unit()), styles.cell);
                } else {
                    blankCell(row, i, styles.cell);
                    textCell(row, i, MetricFormatter.format(metric), styles.mutedCell);
                }
            } else if (value instanceof Number number) {
                numberCell(row, i, number.doubleValue(), styles.integer, styles.cell);
            } else if (value instanceof LocalDate date) {
                dateCell(row, i, date, styles.date, styles.cell);
            } else {
                textCell(row, i, String.valueOf(value), textStyle);
            }
        }
        context.usedDataRow();
    }

    // ------------------------------------------------------------------
    // Native Excel charts
    // ------------------------------------------------------------------
    private void writeChart(SheetContext context, ReportChart chart, Styles styles) {
        if (chart == null || chart.series() == null || chart.series().isEmpty()) {
            writeUnavailableChart(context, chart, styles, "No chart series are available.");
            return;
        }
        if (!context.canUseRows(4)) {
            context.fullWidth("Row limit reached; chart omitted.", styles.warning, 8);
            return;
        }

        int bucketLimit = properties.maxBucketsPerReport();
        boolean truncated = chart.series().stream().anyMatch(s -> s.values() != null && s.values().size() > bucketLimit);
        List<ChartSeries> series = new ArrayList<>();
        for (ChartSeries source : chart.series()) {
            List<ChartValue> values = source.values() == null ? List.of() : source.values();
            List<ChartValue> limited = values.size() > bucketLimit ? values.subList(0, bucketLimit) : values;
            series.add(new ChartSeries(source.name(), limited));
        }
        int categories = series.stream().mapToInt(s -> s.values().size()).max().orElse(0);
        if (categories == 0) {
            writeUnavailableChart(context, chart, styles, "No chartable data is available.");
            return;
        }

        int headerRow = context.rowIndex();
        writeChartSourceTable(context, series, categories, styles);
        if (truncated) {
            context.fullWidth("Chart truncated to " + bucketLimit + " buckets (configured limit).",
                    styles.warning, 8);
        }
        for (String note : chart.availabilityNotes()) {
            context.fullWidth("Availability: " + note, styles.muted, 8);
        }

        if (chart.type() == ChartType.TABLE) {
            context.nextRow();
            return;
        }
        try {
            createNativeChart(context.sheet, chart, series, categories, headerRow);
        } catch (RuntimeException e) {
            context.fullWidth("Native chart could not be created; source data is preserved above.",
                    styles.warning, 8);
        }
        context.nextRow(16);
    }

    private void writeChartSourceTable(SheetContext context, List<ChartSeries> series,
                                       int categories, Styles styles) {
        Object[] header = new Object[series.size() + 1];
        header[0] = "Category";
        for (int i = 0; i < series.size(); i++) {
            header[i + 1] = safe(series.get(i).name());
        }
        writeRow(context, header, styles.header, header.length);
        for (int r = 0; r < categories; r++) {
            Row row = context.nextRow();
            textCell(row, 0, categoryLabel(series, r), styles.cell);
            for (int s = 0; s < series.size(); s++) {
                List<ChartValue> values = series.get(s).values();
                ChartValue value = r < values.size() ? values.get(r) : null;
                if (MetricFormatter.isAvailable(value == null ? null : value.metric())) {
                    numberCell(row, s + 1, value.metric().value().doubleValue(), styles.integer, styles.cell);
                } else {
                    // Missing values are left blank - never written as zero.
                    blankCell(row, s + 1, styles.cell);
                }
            }
            context.usedDataRow();
        }
    }

    private void createNativeChart(XSSFSheet sheet, ReportChart chart, List<ChartSeries> series,
                                   int categories, int headerRow) {
        XSSFDrawing drawing = sheet.createDrawingPatriarch();
        XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0,
                0, headerRow + categories + 3, 10, headerRow + categories + 20);
        XSSFChart nativeChart = drawing.createChart(anchor);
        nativeChart.setTitleText(safe(chart.title()));

        String[] categoryLabels = new String[categories];
        for (int i = 0; i < categories; i++) {
            categoryLabels[i] = categoryLabel(series, i);
        }
        XDDFCategoryDataSource categoriesSource = XDDFDataSourcesFactory.fromArray(categoryLabels);

        if (chart.type() == ChartType.DONUT) {
            XDDFPieChartData pie = (XDDFPieChartData) nativeChart.createData(ChartTypes.DOUGHNUT, null, null);
            pie.setVaryColors(true);
            ChartSeries first = series.getFirst();
            Double[] values = numericValues(first, categories);
            XDDFNumericalDataSource<Double> valueSource = XDDFDataSourcesFactory.fromArray(values);
            XDDFChartData.Series s = pie.addSeries(categoriesSource, valueSource);
            s.setTitle(safe(first.name()));
            nativeChart.plot(pie);
            nativeChart.getOrAddLegend().setPosition(LegendPosition.RIGHT);
            return;
        }

        XDDFCategoryAxis categoryAxis = nativeChart.createCategoryAxis(AxisPosition.BOTTOM);
        XDDFValueAxis valueAxis = nativeChart.createValueAxis(AxisPosition.LEFT);
        ChartTypes type = switch (chart.type()) {
            case LINE -> ChartTypes.LINE;
            case BAR -> ChartTypes.BAR;
            case STACKED_BAR -> ChartTypes.BAR;
            default -> ChartTypes.BAR;
        };
        XDDFChartData data = nativeChart.createData(type, categoryAxis, valueAxis);
        if (chart.type() == ChartType.BAR || chart.type() == ChartType.STACKED_BAR) {
            XDDFBarChartData bars = (XDDFBarChartData) data;
            bars.setBarDirection(BarDirection.COL);
            bars.setBarGrouping(chart.type() == ChartType.STACKED_BAR
                    ? BarGrouping.STACKED : BarGrouping.CLUSTERED);
            if (chart.type() == ChartType.STACKED_BAR) {
                bars.setOverlap((byte) 100);
            }
        }
        for (ChartSeries source : series) {
            XDDFNumericalDataSource<Double> values =
                    XDDFDataSourcesFactory.fromArray(numericValues(source, categories));
            XDDFChartData.Series s = data.addSeries(categoriesSource, values);
            s.setTitle(safe(source.name()));
        }
        nativeChart.plot(data);
        nativeChart.getOrAddLegend().setPosition(LegendPosition.BOTTOM);
    }

    private static String categoryLabel(List<ChartSeries> series, int index) {
        if (series.isEmpty() || index >= series.getFirst().values().size()) {
            return "Category " + (index + 1);
        }
        String label = series.getFirst().values().get(index).label();
        return label == null || label.isBlank() ? "Category " + (index + 1) : label;
    }

    private static Double[] numericValues(ChartSeries series, int categories) {
        Double[] values = new Double[categories];
        for (int i = 0; i < categories; i++) {
            ChartValue value = i < series.values().size() ? series.values().get(i) : null;
            values[i] = MetricFormatter.isAvailable(value == null ? null : value.metric())
                    ? value.metric().value().doubleValue() : null;
        }
        return values;
    }

    private void writeUnavailableChart(SheetContext context, ReportChart chart, Styles styles, String message) {
        if (!context.canUseRows(3)) {
            return;
        }
        context.fullWidth(chart == null ? "Chart" : safe(chart.title()), styles.section, 8);
        context.fullWidth(message + " Missing values are not converted to zero.", styles.warning, 8);
        if (chart != null) {
            for (String note : chart.availabilityNotes()) {
                context.fullWidth("Availability: " + note, styles.muted, 8);
            }
        }
        context.nextRow();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------
    private static String uniqueSheetName(String raw, Set<String> used) {
        String base = raw == null ? "Section"
                : raw.replaceAll("[\\[\\]:*?/\\\\]", " ").replaceAll("\\s+", " ").trim();
        if (base.isBlank()) {
            base = "Section";
        }
        String candidate = truncate(base, MAX_SHEET_NAME_LENGTH);
        if (!used.contains(candidate)) {
            used.add(candidate);
            return candidate;
        }
        // Only fall back to a numeric suffix when the plain name is already taken.
        for (int suffix = 2; ; suffix++) {
            String tail = "-" + suffix;
            String withSuffix = truncate(base, MAX_SHEET_NAME_LENGTH - tail.length()) + tail;
            if (!used.contains(withSuffix)) {
                used.add(withSuffix);
                return withSuffix;
            }
        }
    }

    private static String truncate(String value, int maxLength) {
        String result = value.length() > maxLength ? value.substring(0, maxLength).trim() : value;
        return result.isEmpty() ? "Section" : result;
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "N/A" : value;
    }

    private static void textCell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }

    private static void numberCell(Row row, int column, double value, CellStyle numberStyle, CellStyle fallback) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(numberStyle == null ? fallback : numberStyle);
    }

    private static void dateCell(Row row, int column, LocalDate value, CellStyle style, CellStyle fallback) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style == null ? fallback : style);
    }

    private static void blankCell(Row row, int column, CellStyle style) {
        Cell cell = row.createCell(column, CellType.BLANK);
        cell.setCellStyle(style);
    }

    private static void writeRow(SheetContext context, Object[] values, CellStyle style) {
        writeRow(context, values, style, values.length);
    }

    private static void writeRow(SheetContext context, Object[] values, CellStyle style, int columns) {
        Row row = context.nextRow();
        for (int i = 0; i < columns; i++) {
            Cell cell = row.createCell(i);
            Object value = i < values.length ? values[i] : null;
            if (value instanceof Number number) {
                cell.setCellValue(number.doubleValue());
            } else if (value instanceof LocalDate date) {
                cell.setCellValue(date);
            } else {
                cell.setCellValue(value == null ? "" : String.valueOf(value));
            }
            cell.setCellStyle(style);
        }
        context.usedDataRow();
    }

    private static void addAvailabilityConditionalFormatting(XSSFSheet sheet, int fromRow, int toRow,
                                                             int columns, Styles styles) {
        if (fromRow > toRow) {
            return;
        }
        SheetConditionalFormatting formatting = sheet.getSheetConditionalFormatting();
        CellRangeAddress range = new CellRangeAddress(fromRow, toRow, 0, Math.max(0, columns - 1));
        ConditionalFormattingRule rule = formatting.createConditionalFormattingRule(
                "ISNUMBER(SEARCH(\"N/A\",A" + (fromRow + 1) + "))");
        PatternFormatting pattern = rule.createPatternFormatting();
        pattern.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        pattern.setFillPattern(PatternFormatting.SOLID_FOREGROUND);
        formatting.addConditionalFormatting(new CellRangeAddress[]{range}, rule);
    }

    /** Per-sheet cursor and row budget. */
    private static final class SheetContext {
        private final XSSFSheet sheet;
        private final int rowLimit;
        private int rowIndex;
        private int dataRows;

        private SheetContext(XSSFSheet sheet, int rowLimit) {
            this.sheet = sheet;
            this.rowLimit = rowLimit;
        }

        private int rowIndex() {
            return rowIndex;
        }

        private Row nextRow() {
            return sheet.createRow(rowIndex++);
        }

        private void nextRow(int count) {
            rowIndex += count;
        }

        private void usedDataRow() {
            dataRows++;
        }

        private boolean canUseRows(int count) {
            return dataRows + count <= rowLimit;
        }

        private void titleRow(String value, CellStyle style, int columns) {
            Row row = nextRow();
            for (int i = 0; i < columns; i++) {
                Cell cell = row.createCell(i);
                cell.setCellStyle(style);
                if (i == 0) {
                    cell.setCellValue(value);
                }
            }
        }

        private void labelValue(String label, String value, Styles styles) {
            Row row = nextRow();
            textCell(row, 0, label, styles.label);
            textCell(row, 1, value, styles.cell);
            usedDataRow();
        }

        private void sectionRow(String value, Styles styles, int columns) {
            Row row = nextRow();
            for (int i = 0; i < columns; i++) {
                Cell cell = row.createCell(i);
                cell.setCellStyle(styles.section);
                if (i == 0) {
                    cell.setCellValue(value);
                }
            }
            usedDataRow();
        }

        private void fullWidth(String value, CellStyle style, int columns) {
            Row row = nextRow();
            for (int i = 0; i < columns; i++) {
                Cell cell = row.createCell(i);
                cell.setCellStyle(style);
                if (i == 0) {
                    cell.setCellValue(value);
                }
            }
            usedDataRow();
        }
    }

    /** Shared workbook styles and number formats. */
    private static final class Styles {
        private final XSSFWorkbook workbook;
        private final CellStyle title;
        private final CellStyle section;
        private final CellStyle header;
        private final CellStyle label;
        private final CellStyle cell;
        private final CellStyle alternateCell;
        private final CellStyle mutedCell;
        private final CellStyle muted;
        private final CellStyle warning;
        private final CellStyle integer;
        private final CellStyle percent;
        private final CellStyle date;
        private final CellStyle link;
        private final CellStyle kpiInteger;
        private final CellStyle kpiPercent;
        private final CellStyle kpiDecimal;
        private final CellStyle insightArea;
        private final CellStyle recommendation;
        private final CreationHelper helper;

        private Styles(XSSFWorkbook workbook) {
            this.workbook = workbook;
            this.helper = workbook.getCreationHelper();
            this.title = style(IndexedColors.DARK_GREEN, true, 16, null);
            this.section = style(IndexedColors.TEAL, true, 11, null);
            this.header = style(IndexedColors.TEAL, true, 10, null);
            this.label = style(IndexedColors.WHITE, true, 10, null);
            this.cell = style(IndexedColors.WHITE, false, 10, null);
            this.alternateCell = style(IndexedColors.GREY_25_PERCENT, false, 10, null);
            this.mutedCell = style(IndexedColors.WHITE, false, 10, null);
            this.muted = style(IndexedColors.WHITE, false, 9, null);
            this.warning = style(IndexedColors.LIGHT_YELLOW, false, 9, null);
            this.integer = style(IndexedColors.WHITE, false, 10, "#,##0");
            this.percent = style(IndexedColors.WHITE, false, 10, "0.00\"%\"");
            this.date = style(IndexedColors.WHITE, false, 10, "yyyy-mm-dd");
            this.link = linkStyle();
            this.kpiInteger = highlighted(IndexedColors.LIGHT_GREEN, "#,##0");
            this.kpiPercent = highlighted(IndexedColors.LIGHT_GREEN, "0.00\"%\"");
            this.kpiDecimal = highlighted(IndexedColors.LIGHT_GREEN, "#,##0.00");
            this.insightArea = textOnly(IndexedColors.TEAL, true, 10);
            this.recommendation = textOnly(IndexedColors.DARK_BLUE, false, 10);
        }

        private CellStyle style(IndexedColors background, boolean bold, int size, String format) {
            CellStyle style = workbook.createCellStyle();
            style.setFillForegroundColor(background.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setWrapText(true);
            style.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.CENTER);
            XSSFFont font = workbook.createFont();
            font.setBold(bold);
            font.setFontHeightInPoints((short) size);
            font.setColor(background == IndexedColors.TEAL || background == IndexedColors.DARK_GREEN
                    ? IndexedColors.WHITE.getIndex() : IndexedColors.BLACK.getIndex());
            style.setFont(font);
            if (format != null) {
                style.setDataFormat(helper.createDataFormat().getFormat(format));
            }
            return style;
        }

        private CellStyle numberStyle(String unit) {
            if (unit == null) {
                return integer;
            }
            return switch (unit) {
                case "%" -> percent;
                case "days", "hours" -> style(IndexedColors.WHITE, false, 10, "#,##0.00");
                default -> integer;
            };
        }

        private CellStyle kpiNumberStyle(String unit) {
            if (unit == null) {
                return kpiInteger;
            }
            return switch (unit) {
                case "%" -> kpiPercent;
                case "days", "hours" -> kpiDecimal;
                default -> kpiInteger;
            };
        }

        /** Subtle emphasis for summary KPI values (no heavy borders or decoration). */
        private CellStyle highlighted(IndexedColors background, String format) {
            CellStyle style = workbook.createCellStyle();
            style.setFillForegroundColor(background.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setWrapText(true);
            style.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.CENTER);
            XSSFFont font = workbook.createFont();
            font.setBold(true);
            font.setFontHeightInPoints((short) 11);
            font.setColor(IndexedColors.DARK_GREEN.getIndex());
            style.setFont(font);
            if (format != null) {
                style.setDataFormat(helper.createDataFormat().getFormat(format));
            }
            return style;
        }

        /** A plain-font style used to distinguish insight facts from recommendations. */
        private CellStyle textOnly(IndexedColors color, boolean bold, int size) {
            CellStyle style = workbook.createCellStyle();
            style.setFillForegroundColor(IndexedColors.WHITE.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            style.setWrapText(true);
            style.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.CENTER);
            XSSFFont font = workbook.createFont();
            font.setBold(bold);
            font.setFontHeightInPoints((short) size);
            font.setColor(color.getIndex());
            style.setFont(font);
            return style;
        }

        private CellStyle linkStyle() {
            CellStyle style = workbook.createCellStyle();
            style.setFillForegroundColor(IndexedColors.WHITE.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            XSSFFont font = workbook.createFont();
            font.setFontHeightInPoints((short) 10);
            font.setColor(IndexedColors.BLUE.getIndex());
            font.setUnderline(Font.U_SINGLE);
            style.setFont(font);
            return style;
        }
    }
}
