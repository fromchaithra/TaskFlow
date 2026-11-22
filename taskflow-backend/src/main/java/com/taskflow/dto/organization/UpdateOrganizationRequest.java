package com.taskflow.dto.organization;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateOrganizationRequest(
        @NotBlank(message = "Organization name is required")
        @Size(max = 150)
        String name,

        @Size(max = 100)
        String industry,

        @NotBlank(message = "Timezone is required")
        @Size(max = 64)
        String timezone,

        @NotBlank(message = "Working days are required")
        @Size(max = 50)
        String workingDays
) {
}
