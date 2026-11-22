package com.taskflow.dto.test;

import com.taskflow.dto.auth.UserSummaryDto;
import com.taskflow.entity.TestExecution;
import com.taskflow.enums.TestExecutionResult;

import java.time.Instant;

public record TestExecutionResponse(
        Long id,
        Long runId,
        Long testCaseId,
        String testKey,
        String testTitle,
        TestExecutionResult result,
        String actualResult,
        String notes,
        UserSummaryDto executedBy,
        Instant executedAt,
        Long bugId
) {
    public static TestExecutionResponse from(TestExecution te) {
        return new TestExecutionResponse(
                te.getId(),
                te.getRun().getId(),
                te.getTestCase().getId(),
                te.getTestCase().getTestKey(),
                te.getTestCase().getTitle(),
                te.getResult(),
                te.getActualResult(),
                te.getNotes(),
                te.getExecutedBy() != null ? UserSummaryDto.from(te.getExecutedBy()) : null,
                te.getExecutedAt(),
                te.getBug() != null ? te.getBug().getId() : null
        );
    }
}
