package com.teamflow.dto.task;

import jakarta.validation.constraints.NotNull;

public record TaskOwnerUpdateRequest(
        @NotNull Long ownerId
) {}
