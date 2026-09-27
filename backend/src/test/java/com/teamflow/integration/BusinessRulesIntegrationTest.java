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
 * End-to-end tests for domain business rules that must be rejected by the
 * backend regardless of what the frontend allows: invalid project dates,
 * duplicate project membership, and starting a task with an incomplete
 * dependency.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BusinessRulesIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    private String managerToken;
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
    }

    @Test
    void createProject_withInvalidDateRange_returns400() throws Exception {
        String body = """
                {"name":"Bad Project","description":"d","startDate":"2026-05-01","endDate":"2026-01-01","status":"PLANNED"}
                """;
        mockMvc.perform(post("/api/projects").header("Authorization", "Bearer " + managerToken)
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void addingSameMemberTwice_returnsConflict() throws Exception {
        Long projectId = createProject();
        String addBody = "{\"userId\":" + memberUserId + "}";

        mockMvc.perform(post("/api/projects/" + projectId + "/members")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType("application/json").content(addBody))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/projects/" + projectId + "/members")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType("application/json").content(addBody))
                .andExpect(status().isConflict());
    }

    @Test
    void startingTask_withIncompleteDependency_isRejected() throws Exception {
        Long projectId = createProject();
        addMember(projectId, memberUserId);

        Long taskA = createTask(projectId, "Task A");
        Long taskB = createTask(projectId, "Task B");

        // Task B depends on Task A.
        mockMvc.perform(post("/api/tasks/" + taskB + "/dependencies")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType("application/json").content("{\"dependsOnTaskId\":" + taskA + "}"))
                .andExpect(status().isCreated());

        // Task A is still TODO (incomplete) -> starting Task B must fail.
        mockMvc.perform(patch("/api/tasks/" + taskB + "/status")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType("application/json").content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void circularDependency_isRejected() throws Exception {
        Long projectId = createProject();
        Long taskA = createTask(projectId, "Task A");
        Long taskB = createTask(projectId, "Task B");

        // A depends on B
        mockMvc.perform(post("/api/tasks/" + taskA + "/dependencies")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType("application/json").content("{\"dependsOnTaskId\":" + taskB + "}"))
                .andExpect(status().isCreated());

        // B depends on A -> would close a cycle -> rejected
        mockMvc.perform(post("/api/tasks/" + taskB + "/dependencies")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType("application/json").content("{\"dependsOnTaskId\":" + taskA + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"));
    }

    private Long createProject() throws Exception {
        String body = """
                {"name":"Project","description":"d","startDate":"2026-01-01","endDate":"2026-12-01","status":"IN_PROGRESS"}
                """;
        String response = mockMvc.perform(post("/api/projects").header("Authorization", "Bearer " + managerToken)
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private void addMember(Long projectId, Long userId) throws Exception {
        mockMvc.perform(post("/api/projects/" + projectId + "/members")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType("application/json").content("{\"userId\":" + userId + "}"))
                .andExpect(status().isCreated());
    }

    private Long createTask(Long projectId, String title) throws Exception {
        String body = "{\"title\":\"" + title + "\",\"description\":\"d\",\"priority\":\"MEDIUM\"," +
                "\"startDate\":\"2026-01-01\",\"dueDate\":\"2026-02-01\"}";
        String response = mockMvc.perform(post("/api/projects/" + projectId + "/tasks")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
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
