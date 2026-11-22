package com.taskflow.util.report;

import com.taskflow.dto.report.ReportFormat;
import com.taskflow.dto.report.ReportType;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/** Creates safe, deterministic report download filenames. */
public final class ReportFilenameFactory {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);
    private static final int MAX_SLUG_LENGTH = 80;

    private ReportFilenameFactory() {
    }

    public static String create(ReportType type, ReportFormat format, Instant generatedAt) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(generatedAt, "generatedAt");
        return "taskflow-" + slug(type.name()) + "-" + TIMESTAMP.format(generatedAt) + "." + format.extension();
    }

    /** Strips path components and unsafe characters from arbitrary text. */
    public static String sanitize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "report";
        }
        String normalized = raw.replace('\\', '/');
        int lastSlash = normalized.lastIndexOf('/');
        if (lastSlash >= 0) {
            normalized = normalized.substring(lastSlash + 1);
        }
        normalized = normalized.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (normalized.length() > MAX_SLUG_LENGTH) {
            normalized = normalized.substring(0, MAX_SLUG_LENGTH).replaceAll("-+$", "");
        }
        return normalized.isEmpty() ? "report" : normalized;
    }

    private static String slug(String value) {
        return sanitize(value.replace('_', '-'));
    }
}
