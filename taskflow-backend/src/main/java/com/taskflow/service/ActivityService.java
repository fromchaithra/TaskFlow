package com.taskflow.service;

import com.taskflow.dto.activity.ActivityResponse;
import com.taskflow.dto.common.PagedResponse;
import org.springframework.data.domain.Pageable;

public interface ActivityService {
    PagedResponse<ActivityResponse> recentActivity(Pageable pageable);
}
