package com.taskflow.dto.test;

import com.taskflow.enums.PriorityLevel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateTestCaseRequest(
        @Size(min = 3, max = 200) String title,
        @Size(max = 8000) String description,
        @Size(max = 4000) String preconditions,
        PriorityLevel priority,
        Boolean archived,
        @Valid @Size(max = 100, message = "At most 100 steps are allowed")
        List<CreateTestCaseRequest.TestStepRequest> steps
) {
}
