package com.teamflow.dto.task;

import java.time.Instant;
import java.time.LocalDate;

public record TaskResponse(
        Long id,
        String title,
        String description,
        Long projectId,
        Long ownerId,
        String ownerName,
        String status,
        String priority,
        LocalDate startDate,
        LocalDate dueDate,
        int progress,
        boolean overdue,
        Instant createdAt,
        Instant updatedAt
) {}
