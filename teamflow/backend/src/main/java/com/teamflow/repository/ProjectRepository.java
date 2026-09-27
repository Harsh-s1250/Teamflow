package com.teamflow.repository;

import com.teamflow.entity.Project;
import com.teamflow.entity.ProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    Page<Project> findByStatus(ProjectStatus status, Pageable pageable);
    Page<Project> findByIdIn(List<Long> ids, Pageable pageable);
    long countByStatus(ProjectStatus status);
}
