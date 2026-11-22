package com.taskflow.dto.bug;

import java.time.Instant;

public record BugAttachmentResponse(
        Long id,
        String originalFilename,
        String contentType,
        Long fileSizeBytes,
        String uploadedBy,
        Instant createdAt,
        String downloadUrl
) {
}
