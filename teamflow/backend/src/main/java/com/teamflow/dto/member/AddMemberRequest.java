package com.teamflow.dto.member;

import jakarta.validation.constraints.NotNull;

public record AddMemberRequest(
        @NotNull Long userId
) {}
