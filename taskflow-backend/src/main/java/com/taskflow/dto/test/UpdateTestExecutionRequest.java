package com.taskflow.dto.test;

import com.taskflow.enums.TestExecutionResult;
import jakarta.validation.constraints.Size;

public record UpdateTestExecutionRequest(
        TestExecutionResult result,
        @Size(max = 8000) String actualResult,
        @Size(max = 4000) String notes,
        Long bugId
) {}
