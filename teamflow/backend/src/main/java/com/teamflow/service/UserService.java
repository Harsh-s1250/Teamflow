package com.teamflow.service;

import com.teamflow.dto.user.CreateUserRequest;
import com.teamflow.dto.user.UserResponse;
import com.teamflow.dto.user.UserStatusUpdateRequest;
import com.teamflow.entity.Role;
import com.teamflow.entity.User;
import com.teamflow.exception.BusinessRuleException;
import com.teamflow.exception.ResourceNotFoundException;
import com.teamflow.exception.UnauthorizedException;
import com.teamflow.repository.UserRepository;
import com.teamflow.security.AppUserPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Owns the user lifecycle: creation by a manager, lookup, and
 * activation/deactivation. There is intentionally no self-service
 * registration and no hard delete - see class-level notes on each
 * method for why.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUser currentUser;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, CurrentUser currentUser) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.currentUser = currentUser;
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        // Only a project manager may create new user accounts in this MVP
        // (there is no public self-registration endpoint in scope). The
        // manager acts as the administrative user for user lifecycle - no
        // separate ADMIN role is introduced.
        requireManager();

        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new BusinessRuleException("A user with this email already exists.", true);
        }

        Role role;
        try {
            role = Role.valueOf(request.role());
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("Invalid role. Must be PROJECT_MANAGER or TEAM_MEMBER.");
        }

        // The temporary password is encoded before persistence and never
        // stored or returned in raw form. Bean Validation on the DTO
        // (@Size(min = 8)) is this application's password policy; there is
        // no separate password-reset platform in this MVP, so the manager
        // is responsible for communicating the temporary password to the
        // new user out of band.
        User user = User.builder()
                .name(request.name())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.temporaryPassword()))
                .role(role)
                .active(true)
                .build();

        return toResponse(userRepository.save(user));
    }

    /**
     * Only a project manager may list the full user directory (used to
     * populate "add member" / "reassign owner" pickers and the User
     * Management screen). Filtering is done in memory rather than via a
     * dynamic query builder: at this application's scale (an internal
     * team directory, not a customer database) that is simpler and just
     * as correct, and avoids over-engineering a search system the MVP
     * does not need.
     */
    public List<UserResponse> findAll(Role roleFilter, Boolean activeFilter, String search) {
        requireManager();

        String needle = (search == null || search.isBlank()) ? null : search.trim().toLowerCase();

        return userRepository.findAll().stream()
                .filter(u -> roleFilter == null || u.getRole() == roleFilter)
                .filter(u -> activeFilter == null || u.isActive() == activeFilter)
                .filter(u -> needle == null
                        || u.getName().toLowerCase().contains(needle)
                        || u.getEmail().toLowerCase().contains(needle))
                .map(this::toResponse)
                .toList();
    }

    /** Only a project manager may view another user's account details;
     * team members never need this (their own identity comes from the
     * JWT, and other users' names already appear pre-resolved inside
     * project/task responses). */
    public UserResponse getById(Long userId) {
        requireManager();
        return toResponse(getByIdOrThrow(userId));
    }

    @Transactional
    public UserResponse updateStatus(Long userId, UserStatusUpdateRequest request) {
        requireManager();

        User user = getByIdOrThrow(userId);
        AppUserPrincipal me = currentUser.get();

        if (!request.active() && user.getId().equals(me.getId())) {
            // A manager could otherwise lock themselves out of user
            // management entirely; require another manager to do it.
            throw new BusinessRuleException("You cannot deactivate your own account.");
        }

        // Deactivating a user is intentional and reversible (active can be
        // flipped back to true). It is NOT a delete: existing task
        // ownership and project membership history are left completely
        // untouched here. The only effect elsewhere in the system is that
        // an inactive user can no longer be newly assigned to a task or
        // added to a project (see UserService.getActiveUserOrThrow, used
        // by ProjectService.addMember and TaskService's owner validation).
        // Reassigning their existing active tasks is an explicit
        // out-of-scope future feature, not something this endpoint does
        // silently.
        user.setActive(request.active());
        return toResponse(userRepository.save(user));
    }

    public User getActiveUserOrThrow(Long userId) {
        User user = getByIdOrThrow(userId);
        if (!user.isActive()) {
            throw new BusinessRuleException("User is inactive and cannot be assigned or added to a project.");
        }
        return user;
    }

    public User getByIdOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    private void requireManager() {
        AppUserPrincipal me = currentUser.get();
        if (me.getRole() != Role.PROJECT_MANAGER) {
            throw new UnauthorizedException("Only a project manager can perform this operation.");
        }
    }

    private UserResponse toResponse(User u) {
        return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getRole().name(), u.isActive(), u.getCreatedAt());
    }
}
