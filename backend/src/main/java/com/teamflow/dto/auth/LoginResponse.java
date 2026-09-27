package com.teamflow.dto.auth;

public record LoginResponse(
        String token,
        long expiresInMs,
        Long userId,
        String name,
        String email,
        String role
) {}
