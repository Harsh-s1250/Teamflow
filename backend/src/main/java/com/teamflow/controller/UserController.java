package com.teamflow.controller;

import com.teamflow.dto.user.CreateUserRequest;
import com.teamflow.dto.user.UserResponse;
import com.teamflow.dto.user.UserStatusUpdateRequest;
import com.teamflow.entity.Role;
import com.teamflow.exception.BusinessRuleException;
import com.teamflow.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * User Management. Every operation here is restricted to
 * PROJECT_MANAGER by UserService - the controller stays a thin HTTP
 * adapter and does not itself decide who is authorized (see
 * UserService.requireManager, sourced from the Spring Security context,
 * never from client input).
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> list(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String search) {
        Role roleFilter = parseRoleOrNull(role);
        return ResponseEntity.ok(userService.findAll(roleFilter, active, search));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getById(id));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<UserResponse> updateStatus(@PathVariable Long id,
                                                       @Valid @RequestBody UserStatusUpdateRequest request) {
        return ResponseEntity.ok(userService.updateStatus(id, request));
    }

    private Role parseRoleOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Role.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("Invalid role filter: " + raw);
        }
    }
}
