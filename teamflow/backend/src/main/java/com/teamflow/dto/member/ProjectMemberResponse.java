package com.teamflow.dto.member;

import java.time.Instant;

public record ProjectMemberResponse(
        Long id,
        Long userId,
        String userName,
        String userEmail,
        String role,
        Instant joinedAt
) {}
