package com.att.tdp.issueflow;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class ProjectCreateUpdateDeleteContractIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${security.jwt.secret}")
    private String jwtSecret;

    @Test
    void createProjectWithValidTokenReturnsProjectResponseWithTrimmedValues() throws Exception {
        long ownerId = registerUserAndReturnId(
                "project_create_owner_user",
                "project.create.owner.user@example.com",
                "Project Create Owner",
                "DEVELOPER"
        );
        String token = loginAndGetToken("project_create_owner_user", "SecurePass1!");

        MvcResult result = mockMvc.perform(post("/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "  Project Create Happy  ",
                                "description", "  Project Create Description  ",
                                "ownerId", ownerId
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andReturn();

        JsonNode project = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue("Project Create Happy".equals(project.get("name").asText()));
        assertTrue("Project Create Description".equals(project.get("description").asText()));
        assertTrue(project.get("ownerId").asLong() == ownerId);
    }

    @Test
    void createProjectValidationFailureReturnsBadRequest() throws Exception {
        long ownerId = registerUserAndReturnId(
                "project_create_validation_user",
                "project.create.validation.user@example.com",
                "Project Create Validation",
                "DEVELOPER"
        );
        String token = loginAndGetToken("project_create_validation_user", "SecurePass1!");

        mockMvc.perform(post("/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", " ",
                                "description", "ok",
                                "ownerId", ownerId
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.validationErrors.name").exists());
    }

    @Test
    void createProjectWithMissingOwnerIdReturnsValidationFailed() throws Exception {
        registerUserAndReturnId(
                "project_create_missing_owner_user",
                "project.create.missing.owner.user@example.com",
                "Project Create Missing Owner",
                "DEVELOPER"
        );
        String token = loginAndGetToken("project_create_missing_owner_user", "SecurePass1!");
        Map<String, Object> request = new HashMap<>();
        request.put("name", "Project Missing Owner");
        request.put("description", "Desc");
        request.put("ownerId", null);

        mockMvc.perform(post("/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.validationErrors.ownerId").exists());
    }

    @Test
    void createProjectWithUnknownOwnerReturnsNotFound() throws Exception {
        registerUserAndReturnId(
                "project_create_unknown_owner_user",
                "project.create.unknown.owner.user@example.com",
                "Project Create Unknown Owner",
                "DEVELOPER"
        );
        String token = loginAndGetToken("project_create_unknown_owner_user", "SecurePass1!");

        mockMvc.perform(post("/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Project Unknown Owner",
                                "description", "Unknown owner description",
                                "ownerId", 999999999L
                        ))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/projects"));
    }

    @Test
    void createProjectWithoutTokenReturnsAuthRequired() throws Exception {
        mockMvc.perform(post("/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "No Auth Create",
                                "description", "No Auth Description",
                                "ownerId", 1L
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_REQUIRED"));
    }

    @Test
    void updateProjectWithValidTokenUpdatesNameAndDescription() throws Exception {
        long ownerId = registerUserAndReturnId(
                "project_update_owner_user",
                "project.update.owner.user@example.com",
                "Project Update Owner",
                "DEVELOPER"
        );
        String token = loginAndGetToken("project_update_owner_user", "SecurePass1!");
        long projectId = createProjectAndReturnId(token, ownerId, "Project Before Update", "Before Description");

        mockMvc.perform(patch("/projects/{projectId}", projectId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "  Project After Update  ",
                                "description", "  After Description  "
                        ))))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/projects/{projectId}", projectId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode project = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue("Project After Update".equals(project.get("name").asText()));
        assertTrue("After Description".equals(project.get("description").asText()));
    }

    @Test
    void updateProjectValidationFailureReturnsBadRequest() throws Exception {
        long ownerId = registerUserAndReturnId(
                "project_update_validation_user",
                "project.update.validation.user@example.com",
                "Project Update Validation",
                "DEVELOPER"
        );
        String token = loginAndGetToken("project_update_validation_user", "SecurePass1!");
        long projectId = createProjectAndReturnId(token, ownerId, "Project Update Validation", "Validation Desc");

        mockMvc.perform(patch("/projects/{projectId}", projectId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "",
                                "description", "ok"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.validationErrors.name").exists());
    }

    @Test
    void updateUnknownProjectReturnsNotFound() throws Exception {
        registerUserAndReturnId(
                "project_update_not_found_user",
                "project.update.not.found.user@example.com",
                "Project Update Not Found",
                "DEVELOPER"
        );
        String token = loginAndGetToken("project_update_not_found_user", "SecurePass1!");

        mockMvc.perform(patch("/projects/{projectId}", 999999999L)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Updated Name",
                                "description", "Updated description"
                        ))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/projects/999999999"));
    }

    @Test
    void updateProjectWithoutTokenReturnsAuthRequired() throws Exception {
        mockMvc.perform(patch("/projects/{projectId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "No Auth Update",
                                "description", "No Auth Description"
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_REQUIRED"));
    }

    @Test
    void deleteProjectWithValidTokenSoftDeletesProject() throws Exception {
        long ownerId = registerUserAndReturnId(
                "project_delete_owner_user",
                "project.delete.owner.user@example.com",
                "Project Delete Owner",
                "DEVELOPER"
        );
        String token = loginAndGetToken("project_delete_owner_user", "SecurePass1!");
        long projectId = createProjectAndReturnId(token, ownerId, "Project Delete Happy", "Delete description");

        mockMvc.perform(delete("/projects/{projectId}", projectId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/projects/{projectId}", projectId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(delete("/projects/{projectId}", projectId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void deleteUnknownProjectReturnsNotFound() throws Exception {
        registerUserAndReturnId(
                "project_delete_not_found_user",
                "project.delete.not.found.user@example.com",
                "Project Delete Not Found",
                "DEVELOPER"
        );
        String token = loginAndGetToken("project_delete_not_found_user", "SecurePass1!");

        mockMvc.perform(delete("/projects/{projectId}", 999999999L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/projects/999999999"));
    }

    @Test
    void deleteProjectWithoutTokenReturnsAuthRequired() throws Exception {
        mockMvc.perform(delete("/projects/{projectId}", 1L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_REQUIRED"));
    }

    @Test
    void createProjectWithExpiredTokenReturnsAuthTokenExpired() throws Exception {
        registerUserAndReturnId(
                "project_create_expired_user",
                "project.create.expired.user@example.com",
                "Project Create Expired",
                "DEVELOPER"
        );
        String expiredToken = createExpiredToken("project_create_expired_user");

        mockMvc.perform(post("/projects")
                        .header("Authorization", "Bearer " + expiredToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Create Expired",
                                "description", "Create Expired Description",
                                "ownerId", 1L
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_TOKEN_EXPIRED"));
    }

    @Test
    void updateProjectWithLoggedOutTokenReturnsAuthLoggedOutToken() throws Exception {
        long ownerId = registerUserAndReturnId(
                "project_update_logout_user",
                "project.update.logout.user@example.com",
                "Project Update Logout",
                "DEVELOPER"
        );
        String token = loginAndGetToken("project_update_logout_user", "SecurePass1!");
        long projectId = createProjectAndReturnId(token, ownerId, "Project Logout Before", "Project Logout Desc");

        mockMvc.perform(post("/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/projects/{projectId}", projectId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Should Fail",
                                "description", "Should Fail"
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_LOGGED_OUT_TOKEN"));
    }

    @Test
    void deleteProjectWithInvalidTokenReturnsAuthInvalidToken() throws Exception {
        mockMvc.perform(delete("/projects/{projectId}", 1L)
                        .header("Authorization", "Bearer invalid.token.value"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_INVALID_TOKEN"));
    }

    private long registerUserAndReturnId(String username, String email, String fullName, String role) throws Exception {
        MvcResult result = mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "email", email,
                                "fullName", fullName,
                                "role", role,
                                "password", "SecurePass1!"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private String loginAndGetToken(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", password
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String createExpiredToken(String username) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .issuedAt(Date.from(now.minusSeconds(3600)))
                .expiration(Date.from(now.minusSeconds(60)))
                .signWith(key)
                .compact();
    }

    private long createProjectAndReturnId(String token, long ownerId, String name, String description) throws Exception {
        MvcResult result = mockMvc.perform(post("/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", name,
                                "description", description,
                                "ownerId", ownerId
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }
}



