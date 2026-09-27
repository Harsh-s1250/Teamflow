package com.teamflow.dto.task;

import jakarta.validation.constraints.NotBlank;

public record TaskStatusUpdateRequest(
        @NotBlank String status
) {}
