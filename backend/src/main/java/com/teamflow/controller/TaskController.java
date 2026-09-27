package com.teamflow.controller;

import com.teamflow.dto.task.*;
import com.teamflow.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    // ---- nested under project ----

    @PostMapping("/api/projects/{projectId}/tasks")
    public ResponseEntity<TaskResponse> create(@PathVariable Long projectId, @Valid @RequestBody TaskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(taskService.create(projectId, request));
    }

    @GetMapping("/api/projects/{projectId}/tasks")
    public ResponseEntity<Page<TaskResponse>> listByProject(@PathVariable Long projectId, Pageable pageable) {
        return ResponseEntity.ok(taskService.listByProject(projectId, pageable));
    }

    // ---- direct task resource ----

    @GetMapping("/api/tasks/{id}")
    public ResponseEntity<TaskResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(taskService.getById(id));
    }

    @PutMapping("/api/tasks/{id}")
    public ResponseEntity<TaskResponse> update(@PathVariable Long id, @Valid @RequestBody TaskRequest request) {
        return ResponseEntity.ok(taskService.update(id, request));
    }

    @DeleteMapping("/api/tasks/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        taskService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/api/tasks/{id}/status")
    public ResponseEntity<TaskResponse> updateStatus(@PathVariable Long id,
                                                       @Valid @RequestBody TaskStatusUpdateRequest request) {
        return ResponseEntity.ok(taskService.updateStatus(id, request));
    }

    @PatchMapping("/api/tasks/{id}/owner")
    public ResponseEntity<TaskResponse> updateOwner(@PathVariable Long id,
                                                      @Valid @RequestBody TaskOwnerUpdateRequest request) {
        return ResponseEntity.ok(taskService.updateOwner(id, request));
    }

    @PatchMapping("/api/tasks/{id}/progress")
    public ResponseEntity<TaskResponse> updateProgress(@PathVariable Long id,
                                                         @Valid @RequestBody TaskProgressUpdateRequest request) {
        return ResponseEntity.ok(taskService.updateProgress(id, request));
    }

    // ---- dependencies ----

    @PostMapping("/api/tasks/{taskId}/dependencies")
    public ResponseEntity<TaskDependencyResponse> addDependency(@PathVariable Long taskId,
                                                                  @Valid @RequestBody TaskDependencyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(taskService.addDependency(taskId, request));
    }

    @GetMapping("/api/tasks/{taskId}/dependencies")
    public ResponseEntity<List<TaskDependencyResponse>> listDependencies(@PathVariable Long taskId) {
        return ResponseEntity.ok(taskService.listDependencies(taskId));
    }

    @DeleteMapping("/api/tasks/{taskId}/dependencies/{dependencyId}")
    public ResponseEntity<Void> removeDependency(@PathVariable Long taskId, @PathVariable Long dependencyId) {
        taskService.removeDependency(taskId, dependencyId);
        return ResponseEntity.noContent().build();
    }

    // ---- overdue ----

    @GetMapping("/api/tasks/overdue")
    public ResponseEntity<List<TaskResponse>> overdue() {
        return ResponseEntity.ok(taskService.findOverdue());
    }
}
