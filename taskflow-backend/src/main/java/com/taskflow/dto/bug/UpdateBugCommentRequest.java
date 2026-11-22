package com.taskflow.dto.bug;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateBugCommentRequest(
        @NotBlank(message = "Comment content is required")
        @Size(max = 2000)
        String content
) {
}
