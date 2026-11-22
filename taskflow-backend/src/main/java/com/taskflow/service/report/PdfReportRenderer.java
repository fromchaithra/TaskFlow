package com.taskflow.service.report;

import com.taskflow.dto.report.model.AnalyticsReportData;

/** Renders a renderer-neutral analytics report model into PDF bytes. */
public interface PdfReportRenderer {

    byte[] render(AnalyticsReportData data);
}
