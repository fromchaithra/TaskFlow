package com.taskflow.service.report.impl;

import com.taskflow.config.ReportProperties;
import com.taskflow.dto.analytics.MetricValue;
import com.taskflow.dto.report.model.ChartSeries;
import com.taskflow.dto.report.model.ChartType;
import com.taskflow.dto.report.model.ChartValue;
import com.taskflow.dto.report.model.ReportChart;
import com.taskflow.dto.report.model.ReportKpi;
import com.taskflow.dto.report.model.ReportSection;
import com.taskflow.dto.report.model.ReportTable;
import com.taskflow.dto.report.model.AnalyticsReportData;
import com.taskflow.service.report.PdfReportRenderer;
import com.taskflow.util.report.MetricFormatter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Professional PDF renderer built with PDFBox drawing primitives only. */
@Component
public class PdfBoxReportRenderer implements PdfReportRenderer {

    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final float MARGIN = 40f;
    private static final float CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN;
    private static final float TOP_MARGIN = 60f;
    private static final float BOTTOM_MARGIN = 52f;
    private static final float LINE_HEIGHT = 13f;
    private static final float CHART_HEIGHT = 150f;
    private static final float CARD_HEIGHT = 58f;
    private static final float GAP = 8f;

    private static final PDType1Font REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    private static final Color TEAL = new Color(15, 157, 138);
    private static final Color INK = new Color(32, 43, 53);
    private static final Color MUTED = new Color(86, 101, 113);
    private static final Color LINE = new Color(228, 234, 232);
    private static final Color SUBTLE = new Color(241, 245, 244);
    private static final Color SKY = new Color(56, 139, 207);
    private static final Color EMERALD = new Color(46, 160, 118);
    private static final Color AMBER = new Color(214, 153, 46);
    private static final Color RED = new Color(200, 78, 74);
    private static final Color STEEL = new Color(120, 140, 152);
    private static final Color WHITE = Color.WHITE;

    private static final List<Color> SERIES_COLORS = List.of(TEAL, SKY, EMERALD, AMBER, RED, STEEL);
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private final ReportProperties properties;

    public PdfBoxReportRenderer(ReportProperties properties) {
        this.properties = properties;
    }

    @Override
    public byte[] render(AnalyticsReportData data) {
        if (data == null) {
            throw new IllegalArgumentException("Analytics report data is required.");
        }
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PdfCanvas canvas = new PdfCanvas(document, data, properties);
            renderDocument(canvas, data);
            canvas.close();
            document.save(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate PDF report.", e);
        }
    }

    private void renderDocument(PdfCanvas canvas, AnalyticsReportData data) {
        if (!canvas.ensureSpace(20f)) {
            return;
        }
        if (data.emptyDataset()) {
            canvas.writeWrapped(
                    "No data is available for the selected filters and date range. "
                            + "Missing metrics are shown as unavailable and are never substituted with zero.",
                    BOLD, 10f, INK, 3);
            canvas.newLine();
        }

        if (!data.summaryKpis().isEmpty()) {
            canvas.writeText("Executive Summary", BOLD, 14f, TEAL);
            canvas.newLine(2f);
            renderKpiCards(canvas, data.summaryKpis());
            canvas.newLine(4f);
        }

        for (ReportSection section : data.sections()) {
            // Reserve room for the heading, description and the beginning of its
            // first chart/table so a section title is never orphaned.
            if (!canvas.ensureSpace(sectionMinHeight(section))) {
                break;
            }
            renderSection(canvas, section);
        }

        if (!data.availabilityNotes().isEmpty()) {
            if (!canvas.ensureSpace(40f)) {
                return;
            }
            canvas.writeText("Data availability", BOLD, 11f, TEAL);
            for (String note : data.availabilityNotes()) {
                if (!canvas.ensureSpace(16f)) {
                    return;
                }
                canvas.writeWrapped("• " + note, REGULAR, 9f, MUTED, 2);
            }
        }
    }

    private void renderKpiCards(PdfCanvas canvas, List<ReportKpi> kpis) {
        int limit = properties.maxKpisPerReport();
        List<ReportKpi> shown = kpis.subList(0, Math.min(limit, kpis.size()));
        for (int index = 0; index < shown.size(); index += 3) {
            List<ReportKpi> row = shown.subList(index, Math.min(index + 3, shown.size()));
            if (!canvas.ensureSpace(CARD_HEIGHT + GAP)) {
                return;
            }
            float width = (CONTENT_WIDTH - (row.size() - 1) * GAP) / row.size();
            float top = canvas.y();
            for (int column = 0; column < row.size(); column++) {
                float x = MARGIN + column * (width + GAP);
                canvas.rect(x, top - CARD_HEIGHT, width, CARD_HEIGHT, SUBTLE);
                canvas.rect(x, top - CARD_HEIGHT, 3f, CARD_HEIGHT, TEAL);
                ReportKpi kpi = row.get(column);
                canvas.text(fit(kpi.label(), BOLD, 8f, width - 16f), BOLD, 8f,
                        x + 10f, top - 14f, MUTED);
                canvas.text(fit(MetricFormatter.format(kpi.metric()), BOLD, 13f, width - 16f), BOLD, 13f,
                        x + 10f, top - 33f, INK);
                String comparison = comparison(kpi.metric());
                canvas.text(fit(comparison, REGULAR, 7.5f, width - 16f), REGULAR, 7.5f,
                        x + 10f, top - 45f, TEAL);
                if (!MetricFormatter.isAvailable(kpi.metric())) {
                    canvas.text(fit(MetricFormatter.format(kpi.metric()), REGULAR, 7f, width - 16f),
                            REGULAR, 7f, x + 10f, top - 54f, MUTED);
                }
            }
            canvas.setY(top - CARD_HEIGHT - GAP);
        }
        if (kpis.size() > limit) {
            canvas.writeWrapped("KPI list truncated to " + limit + " of " + kpis.size()
                    + " metrics (configured limit).", REGULAR, 8f, MUTED, 2);
        }
    }

    private static float sectionMinHeight(ReportSection section) {
        float height = 70f;
        if (!section.charts().isEmpty()) {
            height += CHART_HEIGHT + 50f;
        } else if (!section.tables().isEmpty()) {
            height += 60f;
        }
        return height;
    }

    private void renderSection(PdfCanvas canvas, ReportSection section) {
        canvas.writeWrapped(section.title(), BOLD, 12f, TEAL, 2);
        // Thin accent rule makes the section hierarchy immediately scannable.
        float ruleY = canvas.y() + 3f;
        canvas.line(MARGIN, ruleY, MARGIN + 72f, ruleY, TEAL, 1.6f);
        canvas.newLine(0.4f);
        if (section.description() != null && !section.description().isBlank()) {
            canvas.writeWrapped(section.description(), REGULAR, 9f, MUTED, 3);
        }
        if (!section.kpis().isEmpty()) {
            renderKpiCards(canvas, section.kpis());
        }
        if (!section.availabilityNotes().isEmpty()) {
            for (String note : section.availabilityNotes()) {
                canvas.writeWrapped("Availability: " + note, REGULAR, 8f, MUTED, 2);
            }
        }
        for (ReportChart chart : section.charts()) {
            renderChart(canvas, chart);
        }
        for (ReportTable table : section.tables()) {
            renderTable(canvas, table.title(), table);
        }
        // Keep section separation modest; a table already adds its own trailing
        // gap, and excessive spacing can strand the closing notes on a new page.
        canvas.newLine(2f);
    }

    // ------------------------------------------------------------------
    // Charts
    // ------------------------------------------------------------------
    private void renderChart(PdfCanvas canvas, ReportChart chart) {
        if (!canvas.ensureSpace(CHART_HEIGHT + 50f)) {
            return;
        }
        canvas.writeText(fit(chart.title() == null ? "Chart" : chart.title(), BOLD, 10f, CONTENT_WIDTH),
                BOLD, 10f, INK);
        canvas.newLine(3f);

        List<ChartSeries> series = chart.series() == null ? List.of() : chart.series();
        boolean hasValues = series.stream().anyMatch(s -> s.values() != null && !s.values().isEmpty());
        if (!hasValues) {
            canvas.writeWrapped("No chartable data is available for this section.", REGULAR, 9f, MUTED, 1);
            writeAvailabilityNotes(canvas, chart.availabilityNotes());
            return;
        }

        int bucketLimit = properties.maxBucketsPerReport();
        boolean truncated = series.stream().anyMatch(s -> s.values().size() > bucketLimit);

        switch (chart.type()) {
            case LINE, BAR, STACKED_BAR -> renderCartesian(canvas, chart.type(), series, bucketLimit);
            case DONUT -> renderDonut(canvas, series, bucketLimit);
            case TABLE -> renderChartAsTable(canvas, series, bucketLimit);
        }
        if (truncated) {
            canvas.writeWrapped("Chart truncated to " + bucketLimit + " buckets (configured limit).",
                    REGULAR, 8f, MUTED, 1);
        }
        writeAvailabilityNotes(canvas, chart.availabilityNotes());
        canvas.newLine(4f);
    }

    private List<ChartValue> limited(ChartSeries series, int bucketLimit) {
        List<ChartValue> values = series.values() == null ? List.of() : series.values();
        return values.size() > bucketLimit ? values.subList(0, bucketLimit) : values;
    }

    private void renderCartesian(PdfCanvas canvas, ChartType type, List<ChartSeries> series, int bucketLimit) {
        float top = canvas.y() - 6f;
        float bottom = top - CHART_HEIGHT;
        float left = MARGIN + 34f;
        float right = PAGE_WIDTH - MARGIN - 12f;
        float plotHeight = CHART_HEIGHT - 26f;

        List<List<ChartValue>> rows = series.stream().map(s -> limited(s, bucketLimit)).toList();
        int categories = rows.stream().mapToInt(List::size).max().orElse(0);
        if (categories == 0) {
            canvas.writeWrapped("No chartable data is available for this section.", REGULAR, 9f, MUTED, 1);
            return;
        }

        double max = 0d;
        for (List<ChartValue> row : rows) {
            for (ChartValue value : row) {
                Double number = numeric(value);
                if (number != null && number > max) {
                    max = number;
                }
            }
        }
        if (max <= 0d) {
            canvas.writeWrapped("No positive values are available to chart in this period.",
                    REGULAR, 9f, MUTED, 1);
            return;
        }
        double axisMax = niceMax(max);

        canvas.line(left, bottom, right, bottom, LINE, 0.8f);
        canvas.line(left, bottom, left, top, LINE, 0.8f);
        canvas.text("0", REGULAR, 7.5f, left - 16f, bottom - 2f, MUTED);
        canvas.text(formatAxis(axisMax), REGULAR, 7.5f, left - 24f, top - 6f, MUTED);
        canvas.line(left, bottom + plotHeight / 2f, right, bottom + plotHeight / 2f, LINE, 0.5f);

        float slot = (right - left) / categories;
        if (type == ChartType.LINE) {
            renderLines(canvas, rows, left, bottom, slot, plotHeight, axisMax, categories);
        } else {
            renderBars(canvas, type == ChartType.STACKED_BAR, rows, left, bottom, slot, plotHeight, axisMax, categories);
        }

        renderLegend(canvas, series, bottom - 22f);
        if (categories <= 12) {
            List<ChartValue> labels = rows.getFirst();
            for (int i = 0; i < categories; i++) {
                String label = i < labels.size() ? labels.get(i).label() : "";
                canvas.text(fit(label, REGULAR, 6.5f, slot - 2f), REGULAR, 6.5f,
                        left + slot * i + 1f, bottom - 8f, MUTED);
            }
        }
        canvas.setY(bottom - 38f);
    }

    private void renderLines(PdfCanvas canvas, List<List<ChartValue>> rows, float left, float bottom,
                             float slot, float plotHeight, double axisMax, int categories) {
        for (int seriesIndex = 0; seriesIndex < rows.size(); seriesIndex++) {
            Color color = SERIES_COLORS.get(seriesIndex % SERIES_COLORS.size());
            List<ChartValue> values = rows.get(seriesIndex);
            boolean penDown = false;
            float lastX = left;
            float lastY = bottom;
            for (int i = 0; i < categories; i++) {
                ChartValue value = i < values.size() ? values.get(i) : null;
                Double number = numeric(value);
                if (number == null) {
                    penDown = false;
                    continue;
                }
                float x = left + slot * i + slot / 2f;
                float y = bottom + (float) (number / axisMax) * plotHeight;
                if (penDown) {
                    canvas.line(lastX, lastY, x, y, color, 1.6f);
                }
                penDown = true;
                lastX = x;
                lastY = y;
                if (categories <= 12) {
                    canvas.text(fit(MetricFormatter.format(value.metric()), REGULAR, 6.5f, slot - 2f),
                            REGULAR, 6.5f, x - slot / 2f + 1f, y + 3f, MUTED);
                }
            }
        }
    }

    private void renderBars(PdfCanvas canvas, boolean stacked, List<List<ChartValue>> rows, float left,
                            float bottom, float slot, float plotHeight, double axisMax, int categories) {
        float barSlot = stacked ? slot * 0.6f : slot * 0.8f / Math.max(1, rows.size());
        for (int category = 0; category < categories; category++) {
            float stackBase = bottom;
            for (int seriesIndex = 0; seriesIndex < rows.size(); seriesIndex++) {
                List<ChartValue> values = rows.get(seriesIndex);
                ChartValue value = category < values.size() ? values.get(category) : null;
                Double number = numeric(value);
                if (number == null || number <= 0d) {
                    continue;
                }
                float height = (float) (number / axisMax) * plotHeight;
                float x = stacked
                        ? left + slot * category + (slot - barSlot) / 2f
                        : left + slot * category + slot * 0.1f + seriesIndex * barSlot;
                float y = stacked ? stackBase : bottom;
                canvas.rect(x, y, barSlot, stacked ? height : height,
                        SERIES_COLORS.get(seriesIndex % SERIES_COLORS.size()));
                if (stacked) {
                    stackBase += height;
                }
            }
        }
    }

    private void renderDonut(PdfCanvas canvas, List<ChartSeries> series, int bucketLimit) {
        float left = MARGIN + 20f;
        float bottom = canvas.y() - 6f - CHART_HEIGHT;
        float centerX = left + 70f;
        float centerY = bottom + CHART_HEIGHT / 2f;
        float radius = Math.min(62f, CHART_HEIGHT / 2f - 8f);
        float inner = radius * 0.55f;

        List<ChartValue> values = series.isEmpty() ? List.of() : limited(series.getFirst(), bucketLimit);
        double total = values.stream().map(PdfBoxReportRenderer::numeric).filter(v -> v != null && v > 0).mapToDouble(Double::doubleValue).sum();
        if (total <= 0d) {
            canvas.writeWrapped("No positive values are available to chart in this period.",
                    REGULAR, 9f, MUTED, 1);
            canvas.setY(bottom);
            return;
        }
        double angle = 0d;
        for (int i = 0; i < values.size(); i++) {
            Double number = numeric(values.get(i));
            if (number == null || number <= 0d) {
                continue;
            }
            double sweep = number / total * 360d;
            canvas.wedge(centerX, centerY, radius, angle, angle + sweep,
                    SERIES_COLORS.get(i % SERIES_COLORS.size()));
            angle += sweep;
        }
        canvas.wedge(centerX, centerY, inner, 0d, 360d, WHITE);

        float legendX = centerX + radius + 28f;
        float legendY = canvas.y() - 6f;
        for (int i = 0; i < values.size(); i++) {
            ChartValue value = values.get(i);
            Double number = numeric(value);
            String label = fit(value.label() + " — "
                    + (number == null ? "N/A" : MetricFormatter.format(value.metric())),
                    REGULAR, 8f, CONTENT_WIDTH - (legendX - MARGIN) - 10f);
            canvas.text(label, REGULAR, 8f, legendX, legendY, INK);
            legendY -= 14f;
        }
        canvas.setY(bottom - 4f);
    }

    private void renderChartAsTable(PdfCanvas canvas, List<ChartSeries> series, int bucketLimit) {
        List<String> headers = new ArrayList<>();
        headers.add("Period");
        series.forEach(s -> headers.add(s.name()));
        List<List<Object>> rows = new ArrayList<>();
        int categories = series.stream().map(s -> limited(s, bucketLimit).size()).max(Integer::compareTo).orElse(0);
        for (int i = 0; i < categories; i++) {
            List<Object> row = new ArrayList<>();
            row.add(series.isEmpty() ? "" : limited(series.getFirst(), bucketLimit).get(i).label());
            for (ChartSeries s : series) {
                List<ChartValue> values = limited(s, bucketLimit);
                row.add(i < values.size() ? values.get(i).metric() : null);
            }
            rows.add(row);
        }
        renderTable(canvas, new ReportChart("Chart data", ChartType.TABLE, series, List.of()).title(),
                new ReportTable("Chart data", headers, rows));
    }

    private void renderLegend(PdfCanvas canvas, List<ChartSeries> series, float y) {
        float x = MARGIN + 34f;
        for (int i = 0; i < series.size() && i < SERIES_COLORS.size(); i++) {
            canvas.rect(x, y - 6f, 8f, 8f, SERIES_COLORS.get(i));
            String label = fit(series.get(i).name(), REGULAR, 7.5f, 90f);
            canvas.text(label, REGULAR, 7.5f, x + 12f, y - 4f, MUTED);
            x += Math.max(70f, textWidth(REGULAR, 7.5f, label) + 32f);
            if (x > PAGE_WIDTH - MARGIN - 80f) {
                break;
            }
        }
    }

    // ------------------------------------------------------------------
    // Tables
    // ------------------------------------------------------------------
    private void renderTable(PdfCanvas canvas, String title, ReportTable table) {
        if (table == null) {
            return;
        }
        List<String> headers = table.headers() == null ? List.of() : table.headers();
        List<List<Object>> rows = table.rows() == null ? List.of() : table.rows();
        if (headers.isEmpty()) {
            return;
        }
        float[] widths = columnWidths(headers, rows);
        boolean hasTitle = title != null && !title.isBlank();
        // Preflight title, header and at least one data row together so a table
        // heading is never separated from its content by a page break.
        if (!canvas.ensureSpace((hasTitle ? 20f : 0f) + 34f + LINE_HEIGHT + 6f)) {
            return;
        }
        if (hasTitle) {
            canvas.writeText(fit(title, BOLD, 10f, CONTENT_WIDTH), BOLD, 10f, INK);
            canvas.newLine(3f);
        }
        drawTableHeader(canvas, headers, widths);

        boolean insights = "Executive Insights".equals(title);
        int rowIndex = 0;
        for (List<Object> row : rows) {
            List<List<String>> wrapped = new ArrayList<>();
            int lines = 1;
            for (int i = 0; i < headers.size(); i++) {
                String text = cell(row, i);
                List<String> cellLines = wrap(text, REGULAR, 7.5f, widths[i] - 8f);
                if (cellLines.isEmpty()) {
                    cellLines = List.of("");
                }
                wrapped.add(cellLines);
                lines = Math.max(lines, cellLines.size());
            }
            float rowHeight = lines * LINE_HEIGHT + 4f;
            int pageBefore = canvas.pageNumber();
            if (!canvas.ensureSpace(rowHeight + 2f)) {
                break;
            }
            if (canvas.pageNumber() != pageBefore) {
                drawTableHeader(canvas, headers, widths);
            }
            float top = canvas.y();
            if (rowIndex % 2 == 0) {
                canvas.rect(MARGIN, top - rowHeight, CONTENT_WIDTH, rowHeight, SUBTLE);
            }
            float x = MARGIN;
            for (int i = 0; i < headers.size(); i++) {
                // Insights: measured facts stay in ink, recommendations use an
                // accent colour so the two are distinguishable at a glance.
                PDType1Font font = insights && i == 0 ? BOLD : REGULAR;
                Color color = insights && i == 0 ? TEAL : insights && i == 2 ? SKY : INK;
                for (int line = 0; line < wrapped.get(i).size(); line++) {
                    canvas.text(fit(wrapped.get(i).get(line), font, 7.5f, widths[i] - 8f),
                            font, 7.5f, x + 4f, top - 10f - line * LINE_HEIGHT, color);
                }
                x += widths[i];
            }
            canvas.setY(top - rowHeight);
            canvas.line(MARGIN, canvas.y() + 2f, MARGIN + CONTENT_WIDTH, canvas.y() + 2f, LINE, 0.4f);
            rowIndex++;
        }
        canvas.newLine(3f);
    }

    private void drawTableHeader(PdfCanvas canvas, List<String> headers, float[] widths) {
        float top = canvas.y();
        canvas.rect(MARGIN, top - 20f, CONTENT_WIDTH, 20f, TEAL);
        float x = MARGIN;
        for (int i = 0; i < headers.size(); i++) {
            canvas.text(fit(headers.get(i), BOLD, 8f, widths[i] - 8f), BOLD, 8f,
                    x + 4f, top - 13f, WHITE);
            x += widths[i];
        }
        canvas.setY(top - 22f);
    }

    private float[] columnWidths(List<String> headers, List<List<Object>> rows) {
        int count = headers.size();
        float[] widths = new float[count];
        for (int i = 0; i < count; i++) {
            widths[i] = Math.max(30f, textWidth(BOLD, 8f, headers.get(i)) + 10f);
            int sample = Math.min(rows.size(), 30);
            for (int r = 0; r < sample; r++) {
                widths[i] = Math.max(widths[i], Math.min(220f,
                        textWidth(REGULAR, 7.5f, cell(rows.get(r), i)) + 10f));
            }
        }
        float total = 0f;
        for (float width : widths) {
            total += width;
        }
        float scale = CONTENT_WIDTH / total;
        for (int i = 0; i < count; i++) {
            widths[i] *= scale;
        }
        return widths;
    }

    private static String cell(List<Object> row, int index) {
        if (row == null || index >= row.size()) {
            return "";
        }
        Object value = row.get(index);
        if (value == null) {
            return "N/A";
        }
        if (value instanceof MetricValue metric) {
            return MetricFormatter.format(metric);
        }
        return String.valueOf(value);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------
    private static Double numeric(ChartValue value) {
        if (value == null || value.metric() == null || value.metric().value() == null) {
            return null;
        }
        return value.metric().value().doubleValue();
    }

    private static String comparison(MetricValue metric) {
        if (metric == null || metric.previousValue() == null || metric.deltaPercent() == null) {
            return "No comparison";
        }
        String direction = metric.direction() == null ? "FLAT" : metric.direction();
        return direction + " " + MetricFormatter.formatValue(metric.deltaPercent(), "%")
                + " vs " + MetricFormatter.formatValue(metric.previousValue(), metric.unit());
    }

    private static double niceMax(double value) {
        double exponent = Math.floor(Math.log10(value));
        double power = Math.pow(10, exponent);
        double fraction = value / power;
        double nice = fraction <= 1 ? 1 : fraction <= 2 ? 2 : fraction <= 5 ? 5 : 10;
        return nice * power;
    }

    private static String formatAxis(double value) {
        if (value >= 1000) {
            return String.format("%,.0f", value);
        }
        return String.format("%.0f", value);
    }

    private static float textWidth(PDType1Font font, float size, String text) {
        if (text == null) {
            return 0f;
        }
        try {
            return font.getStringWidth(text) / 1000f * size;
        } catch (IOException e) {
            return text.length() * size * 0.5f;
        }
    }

    private static String fit(String text, PDType1Font font, float size, float maxWidth) {
        if (text == null) {
            return "";
        }
        if (textWidth(font, size, text) <= maxWidth) {
            return text;
        }
        String trimmed = text;
        while (trimmed.length() > 1 && textWidth(font, size, trimmed + "...") > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + "...";
    }

    private static List<String> wrap(String text, PDType1Font font, float size, float maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return lines;
        }
        StringBuilder current = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            String candidate = current.length() == 0 ? word : current + " " + word;
            if (textWidth(font, size, candidate) <= maxWidth) {
                current = new StringBuilder(candidate);
            } else {
                if (current.length() > 0) {
                    lines.add(current.toString());
                }
                current = new StringBuilder(word);
            }
        }
        if (current.length() > 0) {
            lines.add(current.toString());
        }
        return lines;
    }

    private static IllegalStateException failure(IOException e) {
        return new IllegalStateException("Failed to write PDF content.", e);
    }

    /** Owns one page at a time so content streams are always closed. */
    private static final class PdfCanvas implements AutoCloseable {

        private final PDDocument document;
        private final AnalyticsReportData data;
        private final ReportProperties properties;
        private PDPageContentStream content;
        private int pageNumber;
        private float y;
        private boolean limitReached;

        private PdfCanvas(PDDocument document, AnalyticsReportData data, ReportProperties properties) {
            this.document = document;
            this.data = data;
            this.properties = properties;
            newPage();
        }

        private float y() {
            return y;
        }

        private int pageNumber() {
            return pageNumber;
        }

        private void setY(float value) {
            this.y = value;
        }

        private boolean ensureSpace(float height) {
            if (limitReached) {
                return false;
            }
            if (y - height < BOTTOM_MARGIN) {
                newPage();
            }
            return !limitReached;
        }

        private void newLine() {
            newLine(1f);
        }

        private void newLine(float multiplier) {
            y -= LINE_HEIGHT * multiplier;
        }

        private void newPage() {
            if (pageNumber >= properties.maxPdfPages()) {
                limitReached = true;
                return;
            }
            if (content != null) {
                finishPage();
            }
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            pageNumber++;
            try {
                content = new PDPageContentStream(document, page);
            } catch (IOException e) {
                throw failure(e);
            }
            y = drawHeader(pageNumber);
        }

        private float drawHeader(int page) {
            float start = PAGE_HEIGHT - MARGIN;
            rect(MARGIN - 7f, start - 11f, 3f, 17f, TEAL);
            text("TaskFlow", BOLD, 16f, MARGIN, start, TEAL);
            text("Analytics Report", REGULAR, 8f, MARGIN + 62f, start, MUTED);
            line(MARGIN, start - 8f, PAGE_WIDTH - MARGIN, start - 8f, TEAL, 1.2f);

            if (page == 1) {
                float cursor = start - 26f;
                List<String> orgLines = wrap("Organization: " + safe(data.metadata().organizationName()),
                        BOLD, 10f, CONTENT_WIDTH);
                for (String lineText : orgLines) {
                    text(fit(lineText, BOLD, 10f, CONTENT_WIDTH), BOLD, 10f, MARGIN, cursor, INK);
                    cursor -= 13f;
                }
                List<String> titleLines = wrap("Report: " + safe(data.metadata().reportTitle()),
                        BOLD, 12f, CONTENT_WIDTH);
                for (String lineText : titleLines) {
                    text(fit(lineText, BOLD, 12f, CONTENT_WIDTH), BOLD, 12f, MARGIN, cursor, TEAL);
                    cursor -= 15f;
                }
                text("Generated: "
                                + (data.metadata().generatedAt() == null ? "N/A"
                                : DATE_TIME.format(data.metadata().generatedAt())),
                        REGULAR, 8f, MARGIN, cursor, MUTED);
                cursor -= 12f;
                text("Period: " + safe(String.valueOf(data.metadata().from()))
                                + " to " + safe(String.valueOf(data.metadata().to())),
                        REGULAR, 8f, MARGIN, cursor, MUTED);
                cursor -= 12f;
                for (String filter : data.metadata().appliedFilters()) {
                    text(fit("Filter: " + filter, REGULAR, 7.5f, CONTENT_WIDTH), REGULAR, 7.5f,
                            MARGIN, cursor, MUTED);
                    cursor -= 11f;
                }
                line(MARGIN, cursor - 2f, PAGE_WIDTH - MARGIN, cursor - 2f, LINE, 0.6f);
                return cursor - 16f;
            }
            text(fit(safe(data.metadata().organizationName()) + "  |  "
                    + safe(data.metadata().reportTitle()), REGULAR, 8f, CONTENT_WIDTH),
                    REGULAR, 8f, MARGIN, start - 22f, MUTED);
            line(MARGIN, start - 30f, PAGE_WIDTH - MARGIN, start - 30f, LINE, 0.6f);
            return start - 44f;
        }

        private void writeText(String value, PDType1Font font, float size, Color color) {
            text(fit(value, font, size, CONTENT_WIDTH), font, size, MARGIN, y, color);
            newLine();
        }

        private void writeWrapped(String value, PDType1Font font, float size, Color color, int maxLines) {
            List<String> lines = wrap(value, font, size, CONTENT_WIDTH);
            for (int i = 0; i < Math.min(maxLines, lines.size()); i++) {
                text(fit(lines.get(i), font, size, CONTENT_WIDTH), font, size, MARGIN, y, color);
                newLine();
            }
        }

        private void text(String value, PDType1Font font, float size, float x, float baseline, Color color) {
            try {
                content.beginText();
                content.setFont(font, size);
                content.setNonStrokingColor(color);
                content.newLineAtOffset(x, baseline);
                content.showText(value == null ? "" : value);
                content.endText();
            } catch (IOException e) {
                throw failure(e);
            }
        }

        private void rect(float x, float bottom, float width, float height, Color color) {
            try {
                content.setNonStrokingColor(color);
                content.addRect(x, bottom, width, height);
                content.fill();
            } catch (IOException e) {
                throw failure(e);
            }
        }

        private void line(float x1, float y1, float x2, float y2, Color color, float width) {
            try {
                content.setStrokingColor(color);
                content.setLineWidth(width);
                content.moveTo(x1, y1);
                content.lineTo(x2, y2);
                content.stroke();
            } catch (IOException e) {
                throw failure(e);
            }
        }

        private void wedge(float cx, float cy, float radius, double startAngle, double endAngle, Color color) {
            try {
                content.setNonStrokingColor(color);
                content.moveTo(cx, cy);
                int steps = Math.max(6, (int) ((endAngle - startAngle) / 6d));
                for (int i = 0; i <= steps; i++) {
                    double angle = Math.toRadians(startAngle + (endAngle - startAngle) * i / steps);
                    content.lineTo(cx + (float) (radius * Math.cos(angle)),
                            cy + (float) (radius * Math.sin(angle)));
                }
                content.closePath();
                content.fill();
            } catch (IOException e) {
                throw failure(e);
            }
        }

        private void drawFooter() {
            line(MARGIN, BOTTOM_MARGIN - 10f, PAGE_WIDTH - MARGIN, BOTTOM_MARGIN - 10f, LINE, 0.6f);
            text("Page " + pageNumber, REGULAR, 7.5f, MARGIN, BOTTOM_MARGIN - 22f, MUTED);
            text(fit(safe(data.metadata().reportTitle()), REGULAR, 7.5f, 220f),
                    REGULAR, 7.5f, MARGIN + 60f, BOTTOM_MARGIN - 22f, MUTED);
            text(fit(safe(data.metadata().organizationName()), REGULAR, 7.5f, 180f),
                    REGULAR, 7.5f, MARGIN + 290f, BOTTOM_MARGIN - 22f, MUTED);
            text("Confidential - internal use only", REGULAR, 7f,
                    PAGE_WIDTH - MARGIN - 140f, BOTTOM_MARGIN - 22f, MUTED);
            if (limitReached) {
                text("Report truncated: maximum page limit reached.", BOLD, 7.5f,
                        MARGIN, BOTTOM_MARGIN - 36f, RED);
            }
        }

        private void finishPage() {
            if (content == null) {
                return;
            }
            drawFooter();
            try {
                content.close();
            } catch (IOException e) {
                throw failure(e);
            }
            content = null;
        }

        @Override
        public void close() {
            finishPage();
        }
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "N/A" : value;
    }

    private static void writeAvailabilityNotes(PdfCanvas canvas, List<String> notes) {
        if (notes == null) {
            return;
        }
        for (String note : notes) {
            canvas.writeWrapped("Availability: " + note, REGULAR, 8f, MUTED, 2);
        }
    }
}
