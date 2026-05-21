package com.att.tdp.issueflow;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ApiErrorResponseIntegrationTest.TestSecurityController.class)
class ApiErrorResponseIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void invalidTokenReturns401WithExplanation() throws Exception {
        mockMvc.perform(get("/users")
                        .header("Authorization", "Bearer bad.token.value"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_INVALID_TOKEN"))
                .andExpect(jsonPath("$.explanation").isNotEmpty())
                .andExpect(jsonPath("$.action").isNotEmpty());
    }

    @Test
    @WithMockUser(username = "developer_forbidden", roles = "DEVELOPER")
    void forbiddenEndpointReturns403WithExplanation() throws Exception {
        mockMvc.perform(get("/test/admin-only"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.explanation").isNotEmpty())
                .andExpect(jsonPath("$.action").isNotEmpty());
    }

    @Test
    void validationFailureReturns400WithExplanation() throws Exception {
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "",
                                "email", "invalid-email",
                                "fullName", "",
                                "role", "DEVELOPER",
                                "password", "short"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.validationErrors").isMap())
                .andExpect(jsonPath("$.explanation").isNotEmpty())
                .andExpect(jsonPath("$.action").isNotEmpty());
    }

    @Test
    void fullNameWithInvalidCharactersIsRejected() throws Exception {
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "name_validation_user",
                                "email", "name.validation@example.com",
                                "fullName", "John!",
                                "role", "DEVELOPER",
                                "password", "SecurePassword1!"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.validationErrors.fullName").exists());
    }

    @Test
    void usernameWithInvalidCharactersIsRejected() throws Exception {
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "john!",
                                "email", "username.validation@example.com",
                                "fullName", "John Doe",
                                "role", "DEVELOPER",
                                "password", "SecurePassword1!"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.validationErrors.username").exists());
    }

    @Test
    void usernameTooShortIsRejected() throws Exception {
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "ab",
                                "email", "username.short.validation@example.com",
                                "fullName", "John Doe",
                                "role", "DEVELOPER",
                                "password", "SecurePassword1!"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.validationErrors.username").exists());
    }

    @Test
    void usernameTooLongIsRejected() throws Exception {
        String longUsername = "a".repeat(61);
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", longUsername,
                                "email", "username.long.validation@example.com",
                                "fullName", "John Doe",
                                "role", "DEVELOPER",
                                "password", "SecurePassword1!"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.validationErrors.username").exists());
    }

    @Test
    void emailWithInvalidFormatIsRejected() throws Exception {
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "email_validation_user",
                                "email", "invalid-email",
                                "fullName", "John Doe",
                                "role", "DEVELOPER",
                                "password", "SecurePassword1!"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.validationErrors.email").exists());
    }

    @Test
    void weakPasswordIsRejected() throws Exception {
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "password_validation_user",
                                "email", "password.validation@example.com",
                                "fullName", "John Doe",
                                "role", "DEVELOPER",
                                "password", "short"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.validationErrors.password").exists());
    }

    @Test
    void validFullNameIsAccepted() throws Exception {
        MvcResult result = mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "valid_full_name_user",
                                "email", "valid.full.name@example.com",
                                "fullName", "Jean-Luc O'Connor",
                                "role", "DEVELOPER",
                                "password", "SecurePassword1!"
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue("Jean-Luc O'Connor".equals(response.get("fullName").asText()));
    }

    @Test
    void validPasswordIsAccepted() throws Exception {
        MvcResult result = mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "valid_password_user",
                                "email", "valid.password@example.com",
                                "fullName", "Valid Password User",
                                "role", "DEVELOPER",
                                "password", "StrongerPass9#"
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue("valid_password_user".equals(response.get("username").asText()));
    }

    @Test
    void duplicateEmailReturnsFriendlyConflictWithoutSqlDetails() throws Exception {
        String duplicateEmail = "duplicate@example.com";

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "first_user",
                                "email", duplicateEmail,
                                "fullName", "First User",
                                "role", "DEVELOPER",
                                "password", "SecurePassword1!"
                        ))))
                .andExpect(status().isOk());

        MvcResult conflictResult = mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "second_user",
                                "email", duplicateEmail,
                                "fullName", "Second User",
                                "role", "DEVELOPER",
                                "password", "SecurePassword1!"
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("USER_EMAIL_EXISTS"))
                .andExpect(jsonPath("$.message").value("Email already in use"))
                .andExpect(jsonPath("$.explanation").isNotEmpty())
                .andExpect(jsonPath("$.action").isNotEmpty())
                .andReturn();

        assertNoSqlLeakage(conflictResult);
    }

    @Test
    void duplicateUsernameReturnsFriendlyConflictWithoutSqlDetails() throws Exception {
        String duplicateUsername = "duplicate_username";

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", duplicateUsername,
                                "email", "first.username.duplicate@example.com",
                                "fullName", "First User",
                                "role", "DEVELOPER",
                                "password", "SecurePassword1!"
                        ))))
                .andExpect(status().isOk());

        MvcResult conflictResult = mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", duplicateUsername,
                                "email", "second.username.duplicate@example.com",
                                "fullName", "Second User",
                                "role", "DEVELOPER",
                                "password", "SecurePassword1!"
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("USER_USERNAME_EXISTS"))
                .andExpect(jsonPath("$.message").value("Username already in use"))
                .andExpect(jsonPath("$.explanation").isNotEmpty())
                .andExpect(jsonPath("$.action").isNotEmpty())
                .andReturn();

        assertNoSqlLeakage(conflictResult);
    }

    @Test
    void notFoundReturns404WithExplanation() throws Exception {
        String token = registerAndLogin("reader_notfound", "reader_notfound@example.com", "DEVELOPER");

        mockMvc.perform(get("/users/{userId}", 999999999L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.explanation").isNotEmpty())
                .andExpect(jsonPath("$.action").isNotEmpty());
    }

    @Test
    void mixedErrorChainValidationConflictAndAuthInSingleScenario() throws Exception {
        // 1) validation failure
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "x",
                                "email", "bad",
                                "fullName", "!",
                                "role", "DEVELOPER",
                                "password", "weak"
                        ))))
                .andExpect(status().isBadRequest());

        // 2) valid create
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "error_chain_user",
                                "email", "error.chain@example.com",
                                "fullName", "Error Chain User",
                                "role", "DEVELOPER",
                                "password", "SecurePassword1!"
                        ))))
                .andExpect(status().isOk());

        // 3) conflict on duplicate username
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "error_chain_user",
                                "email", "error.chain.dup@example.com",
                                "fullName", "Error Chain User Two",
                                "role", "DEVELOPER",
                                "password", "SecurePassword1!"
                        ))))
                .andExpect(status().isConflict());

        // 4) auth failure with invalid token
        mockMvc.perform(get("/users")
                        .header("Authorization", "Bearer not.a.real.token"))
                .andExpect(status().isUnauthorized());
    }

    private String registerAndLogin(String username, String email, String role) throws Exception {
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "email", email,
                                "fullName", "Test User",
                                "role", role,
                                "password", "SecurePassword1!"
                        ))))
                .andExpect(status().isOk());

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", "SecurePassword1!"
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(loginResult.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private void assertNoSqlLeakage(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString().toLowerCase();
        assertFalse(body.contains("insert into"));
        assertFalse(body.contains("constraint"));
        assertFalse(body.contains("duplicate key"));
    }

    @RestController
    static class TestSecurityController {
        @GetMapping("/test/admin-only")
        @PreAuthorize("hasRole('ADMIN')")
        public Map<String, String> adminOnly() {
            return Map.of("message", "ok");
        }
    }
}



