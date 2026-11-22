package com.taskflow.service.report;

import com.taskflow.dto.report.model.AnalyticsReportData;

/** Renders a renderer-neutral analytics report model into XLSX bytes. */
public interface ExcelReportRenderer {

    byte[] render(AnalyticsReportData data);
}
