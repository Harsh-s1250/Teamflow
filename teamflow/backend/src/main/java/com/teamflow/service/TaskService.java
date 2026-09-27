package com.teamflow.service;

import com.teamflow.dto.task.*;
import com.teamflow.entity.*;
import com.teamflow.exception.BusinessRuleException;
import com.teamflow.exception.ResourceNotFoundException;
import com.teamflow.exception.UnauthorizedException;
import com.teamflow.repository.TaskDependencyRepository;
import com.teamflow.repository.TaskRepository;
import com.teamflow.security.AppUserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final TaskDependencyRepository dependencyRepository;
    private final ProjectService projectService;
    private final UserService userService;
    private final CurrentUser currentUser;

    public TaskService(TaskRepository taskRepository, TaskDependencyRepository dependencyRepository,
                        ProjectService projectService, UserService userService, CurrentUser currentUser) {
        this.taskRepository = taskRepository;
        this.dependencyRepository = dependencyRepository;
        this.projectService = projectService;
        this.userService = userService;
        this.currentUser = currentUser;
    }

    // ---------------------------------------------------------------
    // Creation / editing
    // ---------------------------------------------------------------

    @Transactional
    public TaskResponse create(Long projectId, TaskRequest request) {
        Project project = projectService.getOrThrow(projectId);
        projectService.requireManagerOfProject(project);

        if (project.getStatus() == ProjectStatus.CANCELLED) {
            throw new BusinessRuleException("Cancelled projects cannot receive new tasks.");
        }
        if (project.getStatus() == ProjectStatus.COMPLETED) {
            throw new BusinessRuleException("Completed projects cannot normally receive new tasks.");
        }

        validateDateRange(request.startDate(), request.dueDate());

        Long ownerId = request.ownerId();
        if (ownerId != null) {
            validateOwnerBelongsToProject(projectId, ownerId);
        }

        TaskPriority priority = parsePriorityOrDefault(request.priority(), TaskPriority.MEDIUM);

        Task task = Task.builder()
                .title(request.title())
                .description(request.description())
                .projectId(projectId)
                .ownerId(ownerId)
                .status(TaskStatus.TODO)
                .priority(priority)
                .startDate(request.startDate())
                .dueDate(request.dueDate())
                .progress(0)
                .build();

        return toResponse(taskRepository.save(task));
    }

    @Transactional
    public TaskResponse update(Long taskId, TaskRequest request) {
        Task task = getOrThrow(taskId);
        Project project = projectService.getOrThrow(task.getProjectId());
        projectService.requireManagerOfProject(project);

        validateDateRange(request.startDate(), request.dueDate());

        if (request.ownerId() != null) {
            validateOwnerBelongsToProject(task.getProjectId(), request.ownerId());
            task.setOwnerId(request.ownerId());
        }

        task.setTitle(request.title());
        task.setDescription(request.description());
        task.setStartDate(request.startDate());
        task.setDueDate(request.dueDate());
        if (request.priority() != null && !request.priority().isBlank()) {
            task.setPriority(parsePriorityOrDefault(request.priority(), task.getPriority()));
        }

        return toResponse(taskRepository.save(task));
    }

    @Transactional
    public void delete(Long taskId) {
        Task task = getOrThrow(taskId);
        Project project = projectService.getOrThrow(task.getProjectId());
        projectService.requireManagerOfProject(project);

        // Remove dependency edges that reference this task in either
        // direction so no dangling dependency rows remain.
        dependencyRepository.findByTaskId(taskId).forEach(dependencyRepository::delete);
        dependencyRepository.findByDependsOnTaskId(taskId).forEach(dependencyRepository::delete);

        taskRepository.delete(task);
    }

    public TaskResponse getById(Long taskId) {
        Task task = getOrThrow(taskId);
        requireReadAccess(task);
        return toResponse(task);
    }

    public Page<TaskResponse> listByProject(Long projectId, Pageable pageable) {
        Project project = projectService.getOrThrow(projectId);
        projectService.requireMembershipOrManager(project);
        return taskRepository.findByProjectId(projectId, pageable).map(this::toResponse);
    }

    // ---------------------------------------------------------------
    // Status / owner / progress transitions
    // ---------------------------------------------------------------

    @Transactional
    public TaskResponse updateStatus(Long taskId, TaskStatusUpdateRequest request) {
        Task task = getOrThrow(taskId);
        Project project = projectService.getOrThrow(task.getProjectId());
        requireWriteAccess(task, project);

        TaskStatus newStatus;
        try {
            newStatus = TaskStatus.valueOf(request.status());
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("Invalid task status: " + request.status());
        }

        validateTransition(task, newStatus);

        if (newStatus == TaskStatus.IN_PROGRESS) {
            // A task with incomplete required dependencies cannot move into
            // IN_PROGRESS - this is the core "prerequisites" business rule.
            List<Task> incomplete = incompleteDependencies(taskId);
            if (!incomplete.isEmpty()) {
                String titles = incomplete.stream().map(Task::getTitle).reduce((a, b) -> a + ", " + b).orElse("");
                throw new BusinessRuleException(
                        "Task cannot be started because it has incomplete dependencies: " + titles);
            }
        }

        if (newStatus == TaskStatus.COMPLETED && task.getProgress() < 100) {
            throw new BusinessRuleException("A task must be at 100% progress before it can be marked COMPLETED.");
        }

        task.setStatus(newStatus);
        if (newStatus == TaskStatus.COMPLETED) {
            task.setProgress(100);
        }

        return toResponse(taskRepository.save(task));
    }

    @Transactional
    public TaskResponse updateOwner(Long taskId, TaskOwnerUpdateRequest request) {
        Task task = getOrThrow(taskId);
        Project project = projectService.getOrThrow(task.getProjectId());
        // Reassigning ownership is a manager-only operation.
        projectService.requireManagerOfProject(project);

        validateOwnerBelongsToProject(task.getProjectId(), request.ownerId());
        task.setOwnerId(request.ownerId());

        return toResponse(taskRepository.save(task));
    }

    @Transactional
    public TaskResponse updateProgress(Long taskId, TaskProgressUpdateRequest request) {
        Task task = getOrThrow(taskId);
        Project project = projectService.getOrThrow(task.getProjectId());
        requireWriteAccess(task, project);

        int progress = request.progress();
        if (progress < 0 || progress > 100) {
            throw new BusinessRuleException("Progress must be between 0 and 100.");
        }
        if (task.getStatus() == TaskStatus.COMPLETED && progress < 100) {
            throw new BusinessRuleException("Cannot reduce progress below 100% on a completed task; change its status first.");
        }

        task.setProgress(progress);
        if (progress == 100 && task.getStatus() == TaskStatus.IN_PROGRESS) {
            // Reaching 100% does not silently auto-complete the task -
            // COMPLETED is still an explicit status transition - but we
            // allow it to sit at 100% until that transition is made.
        }

        return toResponse(taskRepository.save(task));
    }

    // ---------------------------------------------------------------
    // Dependencies
    // ---------------------------------------------------------------

    @Transactional
    public TaskDependencyResponse addDependency(Long taskId, TaskDependencyRequest request) {
        Task task = getOrThrow(taskId);
        Project project = projectService.getOrThrow(task.getProjectId());
        projectService.requireManagerOfProject(project);

        Long dependsOnId = request.dependsOnTaskId();
        if (dependsOnId.equals(taskId)) {
            throw new BusinessRuleException("A task cannot depend on itself.");
        }

        Task dependsOn = getOrThrow(dependsOnId);
        if (!dependsOn.getProjectId().equals(task.getProjectId())) {
            throw new BusinessRuleException("A dependency must belong to the same project.");
        }

        if (dependencyRepository.existsByTaskIdAndDependsOnTaskId(taskId, dependsOnId)) {
            throw new BusinessRuleException("This dependency already exists.", true);
        }

        // Reject the dependency if adding it would create a cycle: i.e. if
        // dependsOnId can already (transitively) reach taskId.
        if (createsCycle(taskId, dependsOnId)) {
            throw new BusinessRuleException("This dependency would create a circular dependency chain.");
        }

        TaskDependency dependency = TaskDependency.builder()
                .taskId(taskId)
                .dependsOnTaskId(dependsOnId)
                .build();
        dependency = dependencyRepository.save(dependency);

        return new TaskDependencyResponse(dependency.getId(), taskId, dependsOnId, dependsOn.getTitle(),
                dependsOn.getStatus().name(), dependency.getCreatedAt());
    }

    @Transactional
    public void removeDependency(Long taskId, Long dependencyId) {
        Task task = getOrThrow(taskId);
        Project project = projectService.getOrThrow(task.getProjectId());
        projectService.requireManagerOfProject(project);

        TaskDependency dependency = dependencyRepository.findById(dependencyId)
                .orElseThrow(() -> new ResourceNotFoundException("Dependency not found: " + dependencyId));
        if (!dependency.getTaskId().equals(taskId)) {
            throw new ResourceNotFoundException("Dependency not found for this task.");
        }
        dependencyRepository.delete(dependency);
    }

    public List<TaskDependencyResponse> listDependencies(Long taskId) {
        Task task = getOrThrow(taskId);
        requireReadAccess(task);

        return dependencyRepository.findByTaskId(taskId).stream()
                .map(d -> {
                    Task dependsOn = getOrThrow(d.getDependsOnTaskId());
                    return new TaskDependencyResponse(d.getId(), taskId, d.getDependsOnTaskId(),
                            dependsOn.getTitle(), dependsOn.getStatus().name(), d.getCreatedAt());
                })
                .toList();
    }

    /** Breadth-first search over the dependency graph: would adding
     * (taskId -> dependsOnId) let dependsOnId's chain eventually lead back
     * to taskId? If so, the new edge closes a cycle. */
    private boolean createsCycle(Long taskId, Long dependsOnId) {
        Set<Long> visited = new HashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(dependsOnId);

        while (!queue.isEmpty()) {
            Long current = queue.poll();
            if (current.equals(taskId)) {
                return true;
            }
            if (!visited.add(current)) {
                continue;
            }
            dependencyRepository.findByTaskId(current)
                    .forEach(d -> queue.add(d.getDependsOnTaskId()));
        }
        return false;
    }

    private List<Task> incompleteDependencies(Long taskId) {
        return dependencyRepository.findByTaskId(taskId).stream()
                .map(d -> getOrThrow(d.getDependsOnTaskId()))
                .filter(t -> t.getStatus() != TaskStatus.COMPLETED)
                .toList();
    }

    // ---------------------------------------------------------------
    // Overdue
    // ---------------------------------------------------------------

    public List<TaskResponse> findOverdue() {
        AppUserPrincipal me = currentUser.get();
        LocalDate today = LocalDate.now();
        List<Task> candidates = taskRepository.findByStatusNotAndDueDateBefore(TaskStatus.COMPLETED, today);

        return candidates.stream()
                .filter(t -> canRead(t, me))
                .map(this::toResponse)
                .toList();
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    public Task getOrThrow(Long taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));
    }

    private void requireReadAccess(Task task) {
        Project project = projectService.getOrThrow(task.getProjectId());
        projectService.requireMembershipOrManager(project);
    }

    /** A manager of the project, or the task's own owner, may modify the
     * task's status/progress. Any other project member is read-only on it. */
    private void requireWriteAccess(Task task, Project project) {
        AppUserPrincipal me = currentUser.get();
        boolean isManager = projectService.isManagerOfProject(project);
        boolean isOwner = task.getOwnerId() != null && task.getOwnerId().equals(me.getId());
        if (!isManager && !isOwner) {
            throw new UnauthorizedException("Only the task owner or the project's manager can update this task.");
        }
    }

    private boolean canRead(Task task, AppUserPrincipal me) {
        Project project = projectService.getOrThrow(task.getProjectId());
        return projectService.isManagerOfProject(project) || projectService.isMember(project.getId(), me.getId());
    }

    private void validateOwnerBelongsToProject(Long projectId, Long ownerId) {
        userService.getActiveUserOrThrow(ownerId); // also enforces "inactive users cannot be assigned"
        if (!projectService.isMember(projectId, ownerId)) {
            throw new BusinessRuleException("The task owner must be a member of the project.");
        }
    }

    private void validateDateRange(LocalDate start, LocalDate due) {
        if (start != null && due != null && start.isAfter(due)) {
            throw new BusinessRuleException("Task start date cannot be after the due date.");
        }
    }

    private TaskPriority parsePriorityOrDefault(String raw, TaskPriority fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return TaskPriority.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("Invalid task priority: " + raw);
        }
    }

    /** Whitelist of valid status transitions. Kept explicit and small
     * rather than "clever" so every allowed move is obvious at a glance. */
    private static final Map<TaskStatus, Set<TaskStatus>> ALLOWED_TRANSITIONS = Map.of(
            TaskStatus.TODO, EnumSet.of(TaskStatus.IN_PROGRESS, TaskStatus.BLOCKED),
            TaskStatus.IN_PROGRESS, EnumSet.of(TaskStatus.BLOCKED, TaskStatus.COMPLETED, TaskStatus.TODO),
            TaskStatus.BLOCKED, EnumSet.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS),
            TaskStatus.COMPLETED, EnumSet.noneOf(TaskStatus.class)
    );

    private void validateTransition(Task task, TaskStatus newStatus) {
        if (task.getStatus() == newStatus) {
            return; // idempotent no-op
        }
        Set<TaskStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(task.getStatus(), Set.of());
        if (!allowed.contains(newStatus)) {
            throw new BusinessRuleException(
                    "Cannot move task from " + task.getStatus() + " to " + newStatus + ".");
        }
    }

    private TaskResponse toResponse(Task task) {
        String ownerName = null;
        if (task.getOwnerId() != null) {
            ownerName = userService.getByIdOrThrow(task.getOwnerId()).getName();
        }
        boolean overdue = task.isOverdue(LocalDate.now());

        return new TaskResponse(
                task.getId(), task.getTitle(), task.getDescription(), task.getProjectId(),
                task.getOwnerId(), ownerName, task.getStatus().name(), task.getPriority().name(),
                task.getStartDate(), task.getDueDate(), task.getProgress(), overdue,
                task.getCreatedAt(), task.getUpdatedAt()
        );
    }
}
