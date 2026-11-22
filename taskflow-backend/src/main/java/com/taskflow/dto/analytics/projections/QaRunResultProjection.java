package com.taskflow.dto.analytics.projections;

/** QA execution counts grouped by run and result name. */
public record QaRunResultProjection(Long runId, String result, Long count) {
}
