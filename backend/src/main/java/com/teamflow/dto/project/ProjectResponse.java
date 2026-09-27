package com.teamflow.dto.project;

import java.time.Instant;
import java.time.LocalDate;

public record ProjectResponse(
        Long id,
        String name,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        String status,
        Long managerId,
        String managerName,
        int totalTasks,
        int completedTasks,
        double progressPercent,
        Instant createdAt,
        Instant updatedAt
) {}
