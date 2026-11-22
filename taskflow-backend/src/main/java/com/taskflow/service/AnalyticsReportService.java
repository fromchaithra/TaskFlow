package com.taskflow.service;

import com.taskflow.dto.report.AnalyticsReportRequest;
import com.taskflow.dto.report.model.AnalyticsReportData;

/** Builds a renderer-neutral report model from authorized analytics data. */
public interface AnalyticsReportService {

    AnalyticsReportData buildReport(AnalyticsReportRequest request);
}
