package com.teamflow.dto.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record TaskRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 4000) String description,
        Long ownerId,
        String priority,
        LocalDate startDate,
        LocalDate dueDate
) {}
