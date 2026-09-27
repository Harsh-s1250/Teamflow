package com.teamflow.repository;

import com.teamflow.entity.Task;
import com.teamflow.entity.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {
    Page<Task> findByProjectId(Long projectId, Pageable pageable);
    List<Task> findByProjectId(Long projectId);
    long countByProjectId(Long projectId);
    long countByProjectIdAndStatus(Long projectId, TaskStatus status);

    // Used by the dashboard to compute global counts and by overdue
    // detection to scope the "is this due date in the past" check to a
    // manageable candidate set before applying the dynamic overdue rule
    // in the service layer.
    List<Task> findByStatusNotAndDueDateBefore(TaskStatus status, LocalDate date);
    long countByStatus(TaskStatus status);
}
