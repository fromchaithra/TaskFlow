package com.taskflow.dto.test;

import com.taskflow.enums.PriorityLevel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateTestCaseRequest(
        @NotNull Long projectId,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 8000) String description,
        @Size(max = 4000) String preconditions,
        PriorityLevel priority,
        @Valid @Size(max = 100, message = "At most 100 steps are allowed") List<TestStepRequest> steps
) {
    public record TestStepRequest(
            @NotBlank @Size(max = 4000) String action,
            @NotBlank @Size(max = 4000) String expectedResult
    ) {}
}
