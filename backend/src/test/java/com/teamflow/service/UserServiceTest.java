package com.teamflow.service;

import com.teamflow.dto.user.CreateUserRequest;
import com.teamflow.dto.user.UserResponse;
import com.teamflow.dto.user.UserStatusUpdateRequest;
import com.teamflow.entity.Role;
import com.teamflow.entity.User;
import com.teamflow.exception.BusinessRuleException;
import com.teamflow.exception.UnauthorizedException;
import com.teamflow.repository.UserRepository;
import com.teamflow.security.AppUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the User Management feature: manager-only creation,
 * duplicate-email rejection, invalid-role rejection, password hashing
 * (never stored/returned raw), and activation/deactivation.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private CurrentUser currentUser;

    @InjectMocks
    private UserService userService;

    private AppUserPrincipal managerPrincipal;
    private AppUserPrincipal memberPrincipal;

    @BeforeEach
    void setUp() {
        User manager = User.builder().id(1L).name("Manager").email("m@x.com")
                .role(Role.PROJECT_MANAGER).active(true).build();
        User member = User.builder().id(2L).name("Member").email("t@x.com")
                .role(Role.TEAM_MEMBER).active(true).build();
        managerPrincipal = new AppUserPrincipal(manager);
        memberPrincipal = new AppUserPrincipal(member);
    }

    @Test
    void create_succeedsForManager_andHashesThePassword() {
        when(currentUser.get()).thenReturn(managerPrincipal);
        when(userRepository.existsByEmailIgnoreCase("rahul@example.com")).thenReturn(false);
        when(passwordEncoder.encode("temporary-password")).thenReturn("HASHED_VALUE");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(10L);
            u.setCreatedAt(Instant.now());
            return u;
        });

        CreateUserRequest request = new CreateUserRequest("Rahul Sharma", "rahul@example.com",
                "TEAM_MEMBER", "temporary-password");

        UserResponse response = userService.create(request);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.email()).isEqualTo("rahul@example.com");
        assertThat(response.role()).isEqualTo("TEAM_MEMBER");
        assertThat(response.active()).isTrue();

        // The raw password is never persisted - only the encoder's output is.
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("HASHED_VALUE");
        verify(passwordEncoder).encode("temporary-password");
    }

    @Test
    void create_rejectsNonManager() {
        when(currentUser.get()).thenReturn(memberPrincipal);
        CreateUserRequest request = new CreateUserRequest("X", "x@example.com", "TEAM_MEMBER", "temporary-password");

        assertThatThrownBy(() -> userService.create(request)).isInstanceOf(UnauthorizedException.class);
        verifyNoInteractions(userRepository);
    }

    @Test
    void create_rejectsDuplicateEmail() {
        when(currentUser.get()).thenReturn(managerPrincipal);
        when(userRepository.existsByEmailIgnoreCase("dup@example.com")).thenReturn(true);

        CreateUserRequest request = new CreateUserRequest("X", "dup@example.com", "TEAM_MEMBER", "temporary-password");

        assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void create_rejectsInvalidRole() {
        when(currentUser.get()).thenReturn(managerPrincipal);
        when(userRepository.existsByEmailIgnoreCase("x@example.com")).thenReturn(false);

        CreateUserRequest request = new CreateUserRequest("X", "x@example.com", "SUPER_ADMIN", "temporary-password");

        assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Invalid role");
    }

    @Test
    void findAll_rejectsNonManager() {
        when(currentUser.get()).thenReturn(memberPrincipal);
        assertThatThrownBy(() -> userService.findAll(null, null, null)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void updateStatus_deactivatesUser() {
        when(currentUser.get()).thenReturn(managerPrincipal);
        User target = User.builder().id(5L).name("T").email("t2@x.com").role(Role.TEAM_MEMBER).active(true).build();
        when(userRepository.findById(5L)).thenReturn(Optional.of(target));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = userService.updateStatus(5L, new UserStatusUpdateRequest(false));

        assertThat(response.active()).isFalse();
    }

    @Test
    void updateStatus_rejectsSelfDeactivation() {
        when(currentUser.get()).thenReturn(managerPrincipal);
        User self = User.builder().id(1L).name("Manager").email("m@x.com").role(Role.PROJECT_MANAGER).active(true).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(self));

        assertThatThrownBy(() -> userService.updateStatus(1L, new UserStatusUpdateRequest(false)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cannot deactivate your own account");
    }

    @Test
    void updateStatus_rejectsNonManager() {
        when(currentUser.get()).thenReturn(memberPrincipal);
        assertThatThrownBy(() -> userService.updateStatus(5L, new UserStatusUpdateRequest(false)))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void getActiveUserOrThrow_rejectsInactiveUser() {
        User inactive = User.builder().id(9L).name("Inactive").email("i@x.com").role(Role.TEAM_MEMBER).active(false).build();
        when(userRepository.findById(9L)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> userService.getActiveUserOrThrow(9L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("inactive");
    }
}
