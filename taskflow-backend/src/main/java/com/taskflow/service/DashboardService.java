package com.taskflow.service;

import com.taskflow.dto.dashboard.DashboardStatsResponse;

public interface DashboardService {
    DashboardStatsResponse getStats();
}
