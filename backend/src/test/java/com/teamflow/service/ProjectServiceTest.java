package com.teamflow.service;

import com.teamflow.dto.member.AddMemberRequest;
import com.teamflow.dto.project.ProjectRequest;
import com.teamflow.entity.*;
import com.teamflow.exception.BusinessRuleException;
import com.teamflow.exception.UnauthorizedException;
import com.teamflow.repository.ProjectMemberRepository;
import com.teamflow.repository.ProjectRepository;
import com.teamflow.repository.TaskRepository;
import com.teamflow.security.AppUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectMemberRepository memberRepository;
    @Mock private TaskRepository taskRepository;
    @Mock private UserService userService;
    @Mock private CurrentUser currentUser;

    @InjectMocks
    private ProjectService projectService;

    private AppUserPrincipal managerPrincipal;
    private AppUserPrincipal memberPrincipal;

    @BeforeEach
    void setUp() {
        User manager = User.builder().id(1L).name("Manager").email("m@x.com").role(Role.PROJECT_MANAGER).active(true).build();
        User member = User.builder().id(2L).name("Member").email("t@x.com").role(Role.TEAM_MEMBER).active(true).build();
        managerPrincipal = new AppUserPrincipal(manager);
        memberPrincipal = new AppUserPrincipal(member);
    }

    @Test
    void create_rejectsInvalidDateRange() {
        when(currentUser.get()).thenReturn(managerPrincipal);
        ProjectRequest request = new ProjectRequest("P", "d", LocalDate.now().plusDays(5), LocalDate.now(), "PLANNED");

        assertThatThrownBy(() -> projectService.create(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("start date cannot be after");
    }

    @Test
    void create_rejectsNonManager() {
        when(currentUser.get()).thenReturn(memberPrincipal);
        ProjectRequest request = new ProjectRequest("P", "d", LocalDate.now(), LocalDate.now().plusDays(5), "PLANNED");

        assertThatThrownBy(() -> projectService.create(request))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void addMember_rejectsDuplicateMembership() {
        Project project = Project.builder().id(1L).managerId(1L).status(ProjectStatus.IN_PROGRESS).build();
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(currentUser.get()).thenReturn(managerPrincipal);

        User targetUser = User.builder().id(5L).name("X").email("x@x.com").role(Role.TEAM_MEMBER).active(true).build();
        when(userService.getActiveUserOrThrow(5L)).thenReturn(targetUser);
        when(memberRepository.existsByProjectIdAndUserId(1L, 5L)).thenReturn(true);

        assertThatThrownBy(() -> projectService.addMember(1L, new AddMemberRequest(5L)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already a member");
    }

    @Test
    void addMember_rejectsOnCancelledProject() {
        Project project = Project.builder().id(1L).managerId(1L).status(ProjectStatus.CANCELLED).build();
        when(projectRepository.findById(1L)).thenReturn(Optional.of(project));
        when(currentUser.get()).thenReturn(managerPrincipal);

        assertThatThrownBy(() -> projectService.addMember(1L, new AddMemberRequest(5L)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cancelled project");
    }
}
