package com.teamflow.dto.user;

import java.time.Instant;

// Password hash is intentionally never part of any DTO.
public record UserResponse(
        Long id,
        String name,
        String email,
        String role,
        boolean active,
        Instant createdAt
) {}
