package com.taskflow.dto.bug;

import com.taskflow.dto.auth.UserSummaryDto;
import com.taskflow.entity.BugHistory;

import java.time.Instant;

public record BugHistoryResponse(
        Long id,
        String fieldName,
        String oldValue,
        String newValue,
        String remarks,
        UserSummaryDto changedBy,
        Instant createdAt
) {
    public static BugHistoryResponse from(BugHistory history) {
        return new BugHistoryResponse(
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
