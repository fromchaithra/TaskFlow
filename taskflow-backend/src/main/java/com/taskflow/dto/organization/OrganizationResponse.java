package com.taskflow.dto.organization;

import com.taskflow.entity.Organization;

import java.time.Instant;

public record OrganizationResponse(
        Long id,
        String name,
        String slug,
        String industry,
        String timezone,
        String workingDays,
        Instant createdAt,
        Instant updatedAt
) {
    public static OrganizationResponse from(Organization org) {
        return new OrganizationResponse(
                org.getId(),
                org.getName(),
                org.getSlug(),
                org.getIndustry(),
                org.getTimezone(),
                org.getWorkingDays(),
                org.getCreatedAt(),
                org.getUpdatedAt()
        );
    }
}
