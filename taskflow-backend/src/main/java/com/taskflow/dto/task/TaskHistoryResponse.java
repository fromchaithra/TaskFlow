package com.taskflow.dto.task;

import com.taskflow.dto.auth.UserSummaryDto;
import com.taskflow.entity.TaskHistory;

import java.time.Instant;

public record TaskHistoryResponse(
        Long id,
        String fieldName,
        String oldValue,
        String newValue,
        String remarks,
        UserSummaryDto changedBy,
        Instant createdAt
) {
    public static TaskHistoryResponse from(TaskHistory history) {
        return new TaskHistoryResponse(
                history.getId(),
                history.getFieldName(),
                history.getOldValue(),
                history.getNewValue(),
                history.getRemarks(),
                UserSummaryDto.from(history.getChangedBy()),
                history.getCreatedAt()
        );
    }
}
