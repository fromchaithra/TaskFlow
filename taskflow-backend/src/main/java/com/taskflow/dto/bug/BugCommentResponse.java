package com.taskflow.dto.bug;

import com.taskflow.dto.auth.UserSummaryDto;
import com.taskflow.entity.BugComment;

import java.time.Instant;

public record BugCommentResponse(
        Long id,
        UserSummaryDto author,
        String content,
        boolean edited,
        Instant createdAt,
        Instant updatedAt
) {
    public static BugCommentResponse from(BugComment c) {
        return new BugCommentResponse(
                c.getId(),
                UserSummaryDto.from(c.getAuthor()),
                c.getContent(),
                c.isEdited(),
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }
}
