package com.teamflow.dto.dashboard;

import java.util.List;

public record DashboardResponse(
        ProjectCounts projects,
        TaskCounts tasks,
        List<?> overdueTasks,
        List<?> blockedTasks,
        List<?> highPriorityTasks
) {
    public record ProjectCounts(
            long total, long planned, long active, long onHold, long completed, long cancelled
    ) {}

    public record TaskCounts(
            long total, long todo, long inProgress, long blocked, long completed, long overdue
    ) {}
}
