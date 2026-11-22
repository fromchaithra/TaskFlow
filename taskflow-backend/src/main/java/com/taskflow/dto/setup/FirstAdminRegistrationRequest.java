package com.taskflow.dto.setup;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request for public organization registration.
 *
 * <p>Each successful registration creates a new independent organization with
 * the registering user as its ADMIN. The organization name is optional display
 * metadata only: the server always generates the slug and role, and no client
 * supplied organization id or role is ever honoured.</p>
 */
public record FirstAdminRegistrationRequest(
        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 120, message = "Full name must be between 2 and 120 characters")
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 180, message = "Email must not exceed 180 characters")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
        String password,

        @NotBlank(message = "Confirm password is required")
        String confirmPassword,

        @Size(max = 150, message = "Organization name must not exceed 150 characters")
        String organizationName
) {

    /** Backwards-compatible constructor for callers that do not supply an organization name. */
    public FirstAdminRegistrationRequest(String fullName, String email, String password, String confirmPassword) {
        this(fullName, email, password, confirmPassword, null);
    }

    @JsonIgnore
    @AssertTrue(message = "Passwords do not match")
    public boolean isPasswordsMatch() {
        return password != null && password.equals(confirmPassword);
    }
}
