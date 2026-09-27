package com.teamflow.service;

import com.teamflow.dto.task.TaskDependencyRequest;
import com.teamflow.dto.task.TaskProgressUpdateRequest;
import com.teamflow.dto.task.TaskRequest;
import com.teamflow.dto.task.TaskStatusUpdateRequest;
import com.teamflow.entity.*;
import com.teamflow.exception.BusinessRuleException;
import com.teamflow.repository.TaskDependencyRepository;
import com.teamflow.repository.TaskRepository;
import com.teamflow.security.AppUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the core business rules enforced by TaskService:
 * dependency validation, circular dependency rejection, status
 * transitions, and progress/completion rules.
 */
@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock private TaskRepository taskRepository;
    @Mock private TaskDependencyRepository dependencyRepository;
    @Mock private ProjectService projectService;
    @Mock private UserService userService;
    @Mock private CurrentUser currentUser;

    @InjectMocks
    private TaskService taskService;

    private Project project;
    private AppUserPrincipal managerPrincipal;

    @BeforeEach
    void setUp() {
        project = Project.builder()
                .id(1L).name("Demo").status(ProjectStatus.IN_PROGRESS)
                .managerId(10L).startDate(LocalDate.now()).endDate(LocalDate.now().plusDays(30))
                .build();

        User manager = User.builder().id(10L).name("Manager").email("m@x.com")
                .role(Role.PROJECT_MANAGER).active(true).build();
        managerPrincipal = new AppUserPrincipal(manager);
    }

    @Test
    void create_rejectsTaskOnCancelledProject() {
        project.setStatus(ProjectStatus.CANCELLED);
        when(projectService.getOrThrow(1L)).thenReturn(project);
        doNothing().when(projectService).requireManagerOfProject(project);

        TaskRequest request = new TaskRequest("Title", "desc", null, "MEDIUM", LocalDate.now(), LocalDate.now().plusDays(1));

        assertThatThrownBy(() -> taskService.create(1L, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Cancelled projects");
    }

    @Test
    void create_rejectsInvalidDateRange() {
        when(projectService.getOrThrow(1L)).thenReturn(project);
        doNothing().when(projectService).requireManagerOfProject(project);

        TaskRequest request = new TaskRequest("Title", "desc", null, "MEDIUM",
                LocalDate.now().plusDays(5), LocalDate.now());

        assertThatThrownBy(() -> taskService.create(1L, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("start date cannot be after");
    }

    @Test
    void updateStatus_rejectsStartingTaskWithIncompleteDependency() {
        Task task = baseTask(TaskStatus.TODO);
        Task dependency = baseTask(TaskStatus.IN_PROGRESS);
        dependency.setId(2L);

        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(projectService.getOrThrow(1L)).thenReturn(project);
        when(currentUser.get()).thenReturn(managerPrincipal);
        when(projectService.isManagerOfProject(project)).thenReturn(true);

        TaskDependency dep = TaskDependency.builder().id(1L).taskId(1L).dependsOnTaskId(2L).createdAt(Instant.now()).build();
        when(dependencyRepository.findByTaskId(1L)).thenReturn(List.of(dep));
        when(taskRepository.findById(2L)).thenReturn(Optional.of(dependency));

        assertThatThrownBy(() -> taskService.updateStatus(1L, new TaskStatusUpdateRequest("IN_PROGRESS")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("incomplete dependencies");
    }

    @Test
    void updateStatus_allowsStartingTaskWhenDependenciesComplete() {
        Task task = baseTask(TaskStatus.TODO);
        Task dependency = baseTask(TaskStatus.COMPLETED);
        dependency.setId(2L);
        dependency.setProgress(100);

        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(projectService.getOrThrow(1L)).thenReturn(project);
        when(currentUser.get()).thenReturn(managerPrincipal);
        when(projectService.isManagerOfProject(project)).thenReturn(true);

        TaskDependency dep = TaskDependency.builder().id(1L).taskId(1L).dependsOnTaskId(2L).createdAt(Instant.now()).build();
        when(dependencyRepository.findByTaskId(1L)).thenReturn(List.of(dep));
        when(taskRepository.findById(2L)).thenReturn(Optional.of(dependency));
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = taskService.updateStatus(1L, new TaskStatusUpdateRequest("IN_PROGRESS"));

        assertThat(response.status()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void updateStatus_rejectsInvalidTransition() {
        Task task = baseTask(TaskStatus.COMPLETED);
        task.setProgress(100);

        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(projectService.getOrThrow(1L)).thenReturn(project);
        when(currentUser.get()).thenReturn(managerPrincipal);
        when(projectService.isManagerOfProject(project)).thenReturn(true);

        assertThatThrownBy(() -> taskService.updateStatus(1L, new TaskStatusUpdateRequest("TODO")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Cannot move task");
    }

    @Test
    void updateStatus_rejectsCompletingTaskBelow100Percent() {
        Task task = baseTask(TaskStatus.IN_PROGRESS);
        task.setProgress(80);

        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(projectService.getOrThrow(1L)).thenReturn(project);
        when(currentUser.get()).thenReturn(managerPrincipal);
        when(projectService.isManagerOfProject(project)).thenReturn(true);

        assertThatThrownBy(() -> taskService.updateStatus(1L, new TaskStatusUpdateRequest("COMPLETED")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("100% progress");
    }

    @Test
    void updateProgress_rejectsOutOfRangeValues() {
        // Bean Validation (@Min/@Max) blocks this at the DTO layer in the
        // real request path; this test exercises the service-layer guard
        // directly to make sure the rule is also enforced there.
        Task task = baseTask(TaskStatus.IN_PROGRESS);

        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(projectService.getOrThrow(1L)).thenReturn(project);
        when(currentUser.get()).thenReturn(managerPrincipal);
        when(projectService.isManagerOfProject(project)).thenReturn(true);

        assertThatThrownBy(() -> taskService.updateProgress(1L, new TaskProgressUpdateRequest(150)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("between 0 and 100");
    }

    @Test
    void addDependency_rejectsSelfDependency() {
        Task task = baseTask(TaskStatus.TODO);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(projectService.getOrThrow(1L)).thenReturn(project);
        doNothing().when(projectService).requireManagerOfProject(project);

        assertThatThrownBy(() -> taskService.addDependency(1L, new TaskDependencyRequest(1L)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cannot depend on itself");
    }

    @Test
    void addDependency_rejectsCircularDependency() {
        // Graph: Task 1 -> depends on -> Task 2 already exists.
        // Attempting to add Task 2 -> depends on -> Task 1 must be rejected.
        Task task1 = baseTask(TaskStatus.TODO);
        Task task2 = baseTask(TaskStatus.TODO);
        task2.setId(2L);

        when(taskRepository.findById(1L)).thenReturn(Optional.of(task1));
        when(taskRepository.findById(2L)).thenReturn(Optional.of(task2));
        when(projectService.getOrThrow(1L)).thenReturn(project);
        doNothing().when(projectService).requireManagerOfProject(project);
        when(dependencyRepository.existsByTaskIdAndDependsOnTaskId(2L, 1L)).thenReturn(false);

        TaskDependency existing = TaskDependency.builder().id(1L).taskId(1L).dependsOnTaskId(2L).createdAt(Instant.now()).build();
        when(dependencyRepository.findByTaskId(1L)).thenReturn(List.of(existing));
        when(dependencyRepository.findByTaskId(2L)).thenReturn(List.of());

        assertThatThrownBy(() -> taskService.addDependency(2L, new TaskDependencyRequest(1L)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("circular");
    }

    private Task baseTask(TaskStatus status) {
        return Task.builder()
                .id(1L).title("Task").projectId(1L).status(status).priority(TaskPriority.MEDIUM)
                .progress(status == TaskStatus.COMPLETED ? 100 : 0)
                .startDate(LocalDate.now()).dueDate(LocalDate.now().plusDays(5))
                .build();
    }
}
