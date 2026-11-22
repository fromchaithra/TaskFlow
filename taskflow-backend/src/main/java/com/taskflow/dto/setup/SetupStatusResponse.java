package com.taskflow.dto.setup;

/** Public setup status used by the welcome/registration flow. */
public record SetupStatusResponse(boolean adminExists, boolean firstAdminRegistrationAllowed) {
}
