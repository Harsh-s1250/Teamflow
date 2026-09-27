package com.teamflow.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamflow.dto.auth.LoginRequest;
import com.teamflow.entity.Role;
import com.teamflow.entity.User;
import com.teamflow.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end tests for the User Management feature: manager-only user
 * creation/listing/detail/status-change over real HTTP, duplicate-email
 * conflict handling, and rejection of the same operations for a
 * TEAM_MEMBER account (the backend boundary, independent of whatever
 * the React UI chooses to show or hide).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserManagementIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    private String managerToken;
    private String memberToken;
    private Long memberUserId;

    @BeforeEach
    void setUp() throws Exception {
        userRepository.deleteAll();
        userRepository.save(User.builder().name("Manager").email("manager@test.com")
                .passwordHash(passwordEncoder.encode("Password123!")).role(Role.PROJECT_MANAGER).active(true).build());
        User member = userRepository.save(User.builder().name("Member").email("member@test.com")
                .passwordHash(passwordEncoder.encode("Password123!")).role(Role.TEAM_MEMBER).active(true).build());
        memberUserId = member.getId();

        managerToken = loginAndGetToken("manager@test.com");
        memberToken = loginAndGetToken("member@test.com");
    }

    @Test
    void manager_canCreateUser_andResponseNeverContainsPassword() throws Exception {
        String body = """
                {"name":"Rahul Sharma","email":"rahul@example.com","role":"TEAM_MEMBER","temporaryPassword":"temporary-password"}
                """;

        mockMvc.perform(post("/api/users").header("Authorization", "Bearer " + managerToken)
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("rahul@example.com"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void creatingDuplicateEmail_returnsConflict() throws Exception {
        String body = """
                {"name":"Dup","email":"member@test.com","role":"TEAM_MEMBER","temporaryPassword":"temporary-password"}
                """;
        mockMvc.perform(post("/api/users").header("Authorization", "Bearer " + managerToken)
                        .contentType("application/json").content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void teamMember_cannotCreateUser() throws Exception {
        String body = """
                {"name":"X","email":"x@example.com","role":"TEAM_MEMBER","temporaryPassword":"temporary-password"}
                """;
        mockMvc.perform(post("/api/users").header("Authorization", "Bearer " + memberToken)
                        .contentType("application/json").content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticated_cannotCreateUser() throws Exception {
        String body = """
                {"name":"X","email":"x@example.com","role":"TEAM_MEMBER","temporaryPassword":"temporary-password"}
                """;
        mockMvc.perform(post("/api/users").contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void teamMember_cannotListUsers() throws Exception {
        mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void manager_canListAndFilterUsers() throws Exception {
        mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + managerToken)
                        .param("role", "TEAM_MEMBER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("TEAM_MEMBER"));
    }

    @Test
    void manager_canDeactivateAndReactivateUser() throws Exception {
        mockMvc.perform(patch("/api/users/" + memberUserId + "/status")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType("application/json").content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(patch("/api/users/" + memberUserId + "/status")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType("application/json").content("{\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void teamMember_cannotDeactivateUser() throws Exception {
        mockMvc.perform(patch("/api/users/" + memberUserId + "/status")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType("application/json").content("{\"active\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void inactiveUser_cannotBeAddedToProject() throws Exception {
        mockMvc.perform(patch("/api/users/" + memberUserId + "/status")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType("application/json").content("{\"active\":false}"))
                .andExpect(status().isOk());

        String projectBody = """
                {"name":"P","description":"d","startDate":"2026-01-01","endDate":"2026-12-01","status":"PLANNED"}
                """;
        String response = mockMvc.perform(post("/api/projects").header("Authorization", "Bearer " + managerToken)
                        .contentType("application/json").content(projectBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long projectId = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(post("/api/projects/" + projectId + "/members")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType("application/json").content("{\"userId\":" + memberUserId + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"));
    }

    private String loginAndGetToken(String email) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsBytes(new LoginRequest(email, "Password123!"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }
}
