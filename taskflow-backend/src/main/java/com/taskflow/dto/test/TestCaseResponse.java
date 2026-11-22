package com.taskflow.dto.test;

import com.taskflow.dto.auth.UserSummaryDto;
import com.taskflow.entity.TestCase;
import com.taskflow.entity.TestStep;
import com.taskflow.enums.PriorityLevel;

import java.time.Instant;
import java.util.List;

public record TestCaseResponse(
        Long id,
        Long projectId,
        String projectKey,
        String testKey,
        String title,
        String description,
        String preconditions,
        PriorityLevel priority,
        boolean archived,
        UserSummaryDto createdBy,
        List<TestStepResponse> steps,
        Instant createdAt,
        Instant updatedAt
) {
    public record TestStepResponse(Long id, int stepOrder, String action, String expectedResult) {}

    public static TestCaseResponse from(TestCase tc) {
        List<TestStepResponse> steps = tc.getSteps().stream()
                .map(s -> new TestStepResponse(s.getId(), s.getStepOrder(), s.getAction(), s.getExpectedResult()))
                .toList();
        return new TestCaseResponse(
                tc.getId(),
                tc.getProject().getId(),
                tc.getProject().getProjectKey(),
                tc.getTestKey(),
                tc.getTitle(),
                tc.getDescription(),
                tc.getPreconditions(),
                tc.getPriority(),
                tc.isArchived(),
                UserSummaryDto.from(tc.getCreatedBy()),
                steps,
                tc.getCreatedAt(),
                tc.getUpdatedAt()
        );
    }
}
