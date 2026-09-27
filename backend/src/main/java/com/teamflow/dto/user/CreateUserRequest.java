package com.teamflow.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Input for PROJECT_MANAGER-initiated user creation. The field is named
 * temporaryPassword (rather than "password") to make it explicit at the
 * call site that this is a manager-issued starting credential, not a
 * value the new user chose themselves - there is no self-service
 * registration in this system.
 */
public record CreateUserRequest(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank String role,
        @NotBlank @Size(min = 8, max = 100) String temporaryPassword
) {}
