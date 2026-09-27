package com.teamflow.repository;

import com.teamflow.entity.TaskDependency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskDependencyRepository extends JpaRepository<TaskDependency, Long> {
    List<TaskDependency> findByTaskId(Long taskId);
    List<TaskDependency> findByDependsOnTaskId(Long dependsOnTaskId);
    boolean existsByTaskIdAndDependsOnTaskId(Long taskId, Long dependsOnTaskId);
}
