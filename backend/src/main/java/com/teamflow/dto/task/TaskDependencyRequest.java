package com.teamflow.dto.task;

import jakarta.validation.constraints.NotNull;

public record TaskDependencyRequest(
        @NotNull Long dependsOnTaskId
) {}
