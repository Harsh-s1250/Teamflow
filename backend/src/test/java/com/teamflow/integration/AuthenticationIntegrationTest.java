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
 * End-to-end security tests exercising the real HTTP stack: login,
 * missing/invalid token handling, and role-based authorization on a
 * manager-only endpoint. Uses an in-memory H2 database (see
 * application-test.yml) so it runs without an external PostgreSQL
 * instance.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthenticationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        userRepository.save(User.builder()
                .name("Manager").email("manager@test.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .role(Role.PROJECT_MANAGER).active(true).build());
        userRepository.save(User.builder()
                .name("Member").email("member@test.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .role(Role.TEAM_MEMBER).active(true).build());
        userRepository.save(User.builder()
                .name("Inactive").email("inactive@test.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .role(Role.TEAM_MEMBER).active(false).build());
    }

    @Test
    void login_withValidCredentials_returnsToken() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsBytes(new LoginRequest("manager@test.com", "Password123!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.role").value("PROJECT_MANAGER"));
    }

    @Test
    void login_withWrongPassword_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsBytes(new LoginRequest("manager@test.com", "wrong-password"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_forInactiveUser_isRejected() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsBytes(new LoginRequest("inactive@test.com", "Password123!"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_withInvalidToken_returns401() throws Exception {
        mockMvc.perform(get("/api/dashboard").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void teamMember_cannotCreateProject_returns403() throws Exception {
        String token = loginAndGetToken("member@test.com");

        String body = """
                {"name":"New Project","description":"d","startDate":"2026-01-01","endDate":"2026-02-01","status":"PLANNED"}
                """;

        mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void manager_canCreateProject() throws Exception {
        String token = loginAndGetToken("manager@test.com");

        String body = """
                {"name":"New Project","description":"d","startDate":"2026-01-01","endDate":"2026-02-01","status":"PLANNED"}
                """;

        mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("New Project"));
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
