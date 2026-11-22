package com.taskflow.dto.test;

import com.taskflow.enums.PriorityLevel;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateTestCaseRequest(
        @Size(min = 3, max = 200) String title,
        @Size(max = 8000) String description,
        @Size(max = 4000) String preconditions,
        PriorityLevel priority,
        Boolean archived,
        List<CreateTestCaseRequest.TestStepRequest> steps
) {
}
