package com.teamflow.service;

import com.teamflow.dto.member.AddMemberRequest;
import com.teamflow.dto.member.ProjectMemberResponse;
import com.teamflow.dto.project.ProjectRequest;
import com.teamflow.dto.project.ProjectResponse;
import com.teamflow.entity.*;
import com.teamflow.exception.BusinessRuleException;
import com.teamflow.exception.ResourceNotFoundException;
import com.teamflow.exception.UnauthorizedException;
import com.teamflow.repository.ProjectMemberRepository;
import com.teamflow.repository.ProjectRepository;
import com.teamflow.repository.TaskRepository;
import com.teamflow.security.AppUserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final TaskRepository taskRepository;
    private final UserService userService;
    private final CurrentUser currentUser;

    public ProjectService(ProjectRepository projectRepository, ProjectMemberRepository memberRepository,
                           TaskRepository taskRepository, UserService userService, CurrentUser currentUser) {
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
        this.taskRepository = taskRepository;
        this.userService = userService;
        this.currentUser = currentUser;
    }

    @Transactional
    public ProjectResponse create(ProjectRequest request) {
        AppUserPrincipal me = requireManager();
        validateDateRange(request.startDate(), request.endDate());

        // The manager must be an active user; here that is always the
        // caller themselves, whose active status was already enforced at
        // authentication time (inactive users cannot log in / stay
        // authenticated - see AppUserPrincipal.isEnabled()).
        ProjectStatus status = parseStatusOrDefault(request.status(), ProjectStatus.PLANNED);

        Project project = Project.builder()
                .name(request.name())
                .description(request.description())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .status(status)
                .managerId(me.getId())
                .build();
        project = projectRepository.save(project);

        // The manager is automatically a member of their own project so
        // membership-based access checks (e.g. viewing tasks) work for them
        // immediately, without a separate manual step.
        ProjectMember membership = ProjectMember.builder()
                .projectId(project.getId())
                .userId(me.getId())
                .role(Role.PROJECT_MANAGER)
                .build();
        memberRepository.save(membership);

        return toResponse(project);
    }

    @Transactional
    public ProjectResponse update(Long projectId, ProjectRequest request) {
        Project project = getOrThrow(projectId);
        requireManagerOfProject(project);
        validateDateRange(request.startDate(), request.endDate());

        project.setName(request.name());
        project.setDescription(request.description());
        project.setStartDate(request.startDate());
        project.setEndDate(request.endDate());
        if (request.status() != null && !request.status().isBlank()) {
            project.setStatus(parseStatusOrDefault(request.status(), project.getStatus()));
        }

        return toResponse(projectRepository.save(project));
    }

    @Transactional
    public void delete(Long projectId) {
        Project project = getOrThrow(projectId);
        requireManagerOfProject(project);
        projectRepository.delete(project);
    }

    public ProjectResponse getById(Long projectId) {
        Project project = getOrThrow(projectId);
        requireMembershipOrManager(project);
        return toResponse(project);
    }

    public Page<ProjectResponse> list(Pageable pageable) {
        AppUserPrincipal me = currentUser.get();
        // A project manager sees every project (for oversight); a team
        // member only sees projects they are a member of. Filtering happens
        // in the service layer, not just hidden in the UI.
        if (me.getRole() == Role.PROJECT_MANAGER) {
            return projectRepository.findAll(pageable).map(this::toResponse);
        }
        List<Long> memberProjectIds = memberRepository.findByUserId(me.getId()).stream()
                .map(ProjectMember::getProjectId)
                .toList();
        if (memberProjectIds.isEmpty()) {
            return Page.empty(pageable);
        }
        return projectRepository.findByIdIn(memberProjectIds, pageable).map(this::toResponse);
    }

    private ProjectResponse toResponse(Project project) {
        User manager = userService.getByIdOrThrow(project.getManagerId());
        long total = taskRepository.countByProjectId(project.getId());
        long completed = taskRepository.countByProjectIdAndStatus(project.getId(), TaskStatus.COMPLETED);
        double progress = total == 0 ? 0.0 : (completed * 100.0) / total;

        return new ProjectResponse(
                project.getId(), project.getName(), project.getDescription(),
                project.getStartDate(), project.getEndDate(), project.getStatus().name(),
                project.getManagerId(), manager.getName(),
                (int) total, (int) completed, Math.round(progress * 10.0) / 10.0,
                project.getCreatedAt(), project.getUpdatedAt()
        );
    }

    // ---- membership management ----

    @Transactional
    public ProjectMemberResponse addMember(Long projectId, AddMemberRequest request) {
        Project project = getOrThrow(projectId);
        requireManagerOfProject(project);

        if (project.getStatus() == ProjectStatus.CANCELLED) {
            throw new BusinessRuleException("Cannot add members to a cancelled project.");
        }

        User user = userService.getActiveUserOrThrow(request.userId());

        if (memberRepository.existsByProjectIdAndUserId(projectId, user.getId())) {
            throw new BusinessRuleException("This user is already a member of the project.", true);
        }

        ProjectMember member = ProjectMember.builder()
                .projectId(projectId)
                .userId(user.getId())
                .role(user.getRole())
                .build();
        // Database uniqueness on (project_id, user_id) is the final backstop
        // against a race between the existsBy check above and this insert.
        member = memberRepository.save(member);

        return new ProjectMemberResponse(member.getId(), user.getId(), user.getName(), user.getEmail(),
                member.getRole().name(), member.getJoinedAt());
    }

    @Transactional
    public void removeMember(Long projectId, Long userId) {
        Project project = getOrThrow(projectId);
        requireManagerOfProject(project);

        if (userId.equals(project.getManagerId())) {
            throw new BusinessRuleException("The project manager cannot be removed from the project.");
        }
        if (!memberRepository.existsByProjectIdAndUserId(projectId, userId)) {
            throw new ResourceNotFoundException("This user is not a member of the project.");
        }
        memberRepository.deleteByProjectIdAndUserId(projectId, userId);
    }

    public List<ProjectMemberResponse> listMembers(Long projectId) {
        Project project = getOrThrow(projectId);
        requireMembershipOrManager(project);

        return memberRepository.findByProjectId(projectId).stream()
                .map(m -> {
                    User u = userService.getByIdOrThrow(m.getUserId());
                    return new ProjectMemberResponse(m.getId(), u.getId(), u.getName(), u.getEmail(),
                            m.getRole().name(), m.getJoinedAt());
                })
                .toList();
    }

    // ---- shared authorization / lookup helpers (also used by TaskService) ----

    public Project getOrThrow(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));
    }

    /** True if the current user is the project's manager (global role AND owner of this project). */
    public boolean isManagerOfProject(Project project) {
        AppUserPrincipal me = currentUser.get();
        return me.getRole() == Role.PROJECT_MANAGER && me.getId().equals(project.getManagerId());
    }

    public void requireManagerOfProject(Project project) {
        if (!isManagerOfProject(project)) {
            throw new UnauthorizedException("Only the project's manager can perform this operation.");
        }
    }

    /** Anyone who belongs to the project, or the (global) manager of it, may view it. */
    public void requireMembershipOrManager(Project project) {
        AppUserPrincipal me = currentUser.get();
        boolean isMember = memberRepository.existsByProjectIdAndUserId(project.getId(), me.getId());
        boolean isManager = isManagerOfProject(project);
        if (!isMember && !isManager) {
            throw new UnauthorizedException("You do not have access to this project.");
        }
    }

    public boolean isMember(Long projectId, Long userId) {
        return memberRepository.existsByProjectIdAndUserId(projectId, userId);
    }

    private AppUserPrincipal requireManager() {
        AppUserPrincipal me = currentUser.get();
        if (me.getRole() != Role.PROJECT_MANAGER) {
            throw new UnauthorizedException("Only a project manager can perform this operation.");
        }
        return me;
    }

    private void validateDateRange(java.time.LocalDate start, java.time.LocalDate end) {
        if (start != null && end != null && start.isAfter(end)) {
            throw new BusinessRuleException("Project start date cannot be after the end date.");
        }
    }

    private ProjectStatus parseStatusOrDefault(String raw, ProjectStatus fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return ProjectStatus.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("Invalid project status: " + raw);
        }
    }
}
