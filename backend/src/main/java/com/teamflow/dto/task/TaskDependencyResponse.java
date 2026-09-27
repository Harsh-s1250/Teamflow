package com.teamflow.dto.task;

import java.time.Instant;

public record TaskDependencyResponse(
        Long id,
        Long taskId,
        Long dependsOnTaskId,
        String dependsOnTaskTitle,
        String dependsOnTaskStatus,
        Instant createdAt
) {}
