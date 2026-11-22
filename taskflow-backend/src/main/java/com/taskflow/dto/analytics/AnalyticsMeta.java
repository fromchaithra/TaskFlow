package com.taskflow.dto.analytics;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/** Metadata attached to every analytics response. */
public record AnalyticsMeta(
        LocalDate from,
        LocalDate to,
        Instant generatedAt,
        String basis,
        boolean compared,
        Map<String, DataAvailability> notes
) {

    public static AnalyticsMeta of(AnalyticsQuery query, String basis) {
        boolean compared = query.compareFrom() != null && query.compareTo() != null;
        return new AnalyticsMeta(
                query.from(),
                query.to(),
                Instant.now(),
                basis,
                compared,
                new HashMap<>()
        );
    }
}
