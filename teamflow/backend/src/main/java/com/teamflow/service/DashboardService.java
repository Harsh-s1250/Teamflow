package com.teamflow.service;

import com.teamflow.dto.dashboard.DashboardResponse;
import com.teamflow.dto.task.TaskResponse;
import com.teamflow.entity.*;
import com.teamflow.repository.ProjectMemberRepository;
import com.teamflow.repository.ProjectRepository;
import com.teamflow.repository.TaskRepository;
import com.teamflow.security.AppUserPrincipal;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class DashboardService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final TaskRepository taskRepository;
    private final TaskService taskService;
    private final CurrentUser currentUser;

    public DashboardService(ProjectRepository projectRepository, ProjectMemberRepository memberRepository,
                             TaskRepository taskRepository, TaskService taskService, CurrentUser currentUser) {
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
        this.taskRepository = taskRepository;
        this.taskService = taskService;
        this.currentUser = currentUser;
    }

    public DashboardResponse get() {
        AppUserPrincipal me = currentUser.get();

        List<Long> visibleProjectIds;
        if (me.getRole() == Role.PROJECT_MANAGER) {
            visibleProjectIds = projectRepository.findAll().stream().map(Project::getId).toList();
        } else {
            visibleProjectIds = memberRepository.findByUserId(me.getId()).stream()
                    .map(ProjectMember::getProjectId).toList();
        }

        List<Project> projects = projectRepository.findAllById(visibleProjectIds);
        List<Task> tasks = visibleProjectIds.stream()
                .flatMap(id -> taskRepository.findByProjectId(id).stream())
                .toList();

        long planned = projects.stream().filter(p -> p.getStatus() == ProjectStatus.PLANNED).count();
        long active = projects.stream().filter(p -> p.getStatus() == ProjectStatus.IN_PROGRESS).count();
        long onHold = projects.stream().filter(p -> p.getStatus() == ProjectStatus.ON_HOLD).count();
        long completedP = projects.stream().filter(p -> p.getStatus() == ProjectStatus.COMPLETED).count();
        long cancelled = projects.stream().filter(p -> p.getStatus() == ProjectStatus.CANCELLED).count();

        long todo = tasks.stream().filter(t -> t.getStatus() == TaskStatus.TODO).count();
        long inProgress = tasks.stream().filter(t -> t.getStatus() == TaskStatus.IN_PROGRESS).count();
        long blocked = tasks.stream().filter(t -> t.getStatus() == TaskStatus.BLOCKED).count();
        long completedT = tasks.stream().filter(t -> t.getStatus() == TaskStatus.COMPLETED).count();
        LocalDate today = LocalDate.now();
        long overdue = tasks.stream().filter(t -> t.isOverdue(today)).count();

        List<TaskResponse> overdueTasks = taskService.findOverdue();
        List<TaskResponse> blockedTasks = tasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.BLOCKED)
                .map(t -> taskService.getById(t.getId()))
                .toList();
        List<TaskResponse> highPriorityTasks = tasks.stream()
                .filter(t -> t.getPriority() == TaskPriority.HIGH || t.getPriority() == TaskPriority.CRITICAL)
                .map(t -> taskService.getById(t.getId()))
                .toList();

        return new DashboardResponse(
                new DashboardResponse.ProjectCounts(projects.size(), planned, active, onHold, completedP, cancelled),
                new DashboardResponse.TaskCounts(tasks.size(), todo, inProgress, blocked, completedT, overdue),
                overdueTasks,
                blockedTasks,
                highPriorityTasks
        );
    }
}
