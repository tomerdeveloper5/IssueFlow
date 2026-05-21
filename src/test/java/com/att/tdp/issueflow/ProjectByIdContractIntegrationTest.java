package com.att.tdp.issueflow;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class ProjectByIdContractIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${security.jwt.secret}")
    private String jwtSecret;

    @Test
    void getProjectByIdWithValidTokenReturnsExpectedFieldsAndValues() throws Exception {
        long ownerId = registerUserAndReturnId(
                "project_by_id_owner_user",
                "project.by.id.owner.user@example.com",
                "Project ById Owner",
                "DEVELOPER"
        );
        String token = loginAndGetToken("project_by_id_owner_user", "SecurePass1!");
        long projectId = createProjectAndReturnId(token, ownerId, "Project ById Happy", "Project ById Description");

        MvcResult result = mockMvc.perform(get("/projects/{projectId}", projectId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode project = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(project.size() == 4);
        assertTrue(project.get("id").asLong() == projectId);
        assertTrue("Project ById Happy".equals(project.get("name").asText()));
        assertTrue("Project ById Description".equals(project.get("description").asText()));
        assertTrue(project.get("ownerId").asLong() == ownerId);
    }

    @Test
    void getProjectByIdWithoutTokenReturnsAuthRequired() throws Exception {
        mockMvc.perform(get("/projects/{projectId}", 1L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_REQUIRED"))
                .andExpect(jsonPath("$.path").value("/projects/1"))
                .andExpect(jsonPath("$.explanation").isNotEmpty())
                .andExpect(jsonPath("$.action").isNotEmpty());
    }

    @Test
    void getProjectByIdWithInvalidTokenReturnsAuthInvalidToken() throws Exception {
        mockMvc.perform(get("/projects/{projectId}", 1L)
                        .header("Authorization", "Bearer invalid.token.value"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_INVALID_TOKEN"))
                .andExpect(jsonPath("$.path").value("/projects/1"))
                .andExpect(jsonPath("$.explanation").isNotEmpty())
                .andExpect(jsonPath("$.action").isNotEmpty());
    }

    @Test
    void getProjectByIdWithExpiredTokenReturnsAuthTokenExpired() throws Exception {
        registerUserAndReturnId(
                "project_by_id_expired_user",
                "project.by.id.expired.user@example.com",
                "Project ById Expired",
                "DEVELOPER"
        );
        String expiredToken = createExpiredToken("project_by_id_expired_user");

        mockMvc.perform(get("/projects/{projectId}", 1L)
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_TOKEN_EXPIRED"))
                .andExpect(jsonPath("$.path").value("/projects/1"))
                .andExpect(jsonPath("$.explanation").isNotEmpty())
                .andExpect(jsonPath("$.action").isNotEmpty());
    }

    @Test
    void getProjectByIdWithLoggedOutTokenReturnsAuthLoggedOutToken() throws Exception {
        registerUserAndReturnId(
                "project_by_id_logout_user",
                "project.by.id.logout.user@example.com",
                "Project ById Logout",
                "DEVELOPER"
        );
        String token = loginAndGetToken("project_by_id_logout_user", "SecurePass1!");

        mockMvc.perform(post("/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/projects/{projectId}", 1L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_LOGGED_OUT_TOKEN"))
                .andExpect(jsonPath("$.path").value("/projects/1"))
                .andExpect(jsonPath("$.explanation").isNotEmpty())
                .andExpect(jsonPath("$.action").isNotEmpty());
    }

    @Test
    void getProjectByIdWithValidTokenAndUnknownProjectReturnsNotFound() throws Exception {
        registerUserAndReturnId(
                "project_by_id_missing_user",
                "project.by.id.missing.user@example.com",
                "Project ById Missing",
                "DEVELOPER"
        );
        String token = loginAndGetToken("project_by_id_missing_user", "SecurePass1!");

        mockMvc.perform(get("/projects/{projectId}", 999999999L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/projects/999999999"))
                .andExpect(jsonPath("$.explanation").isNotEmpty())
                .andExpect(jsonPath("$.action").isNotEmpty());
    }

    @Test
    void getProjectByIdForSoftDeletedProjectReturnsNotFound() throws Exception {
        long ownerId = registerUserAndReturnId(
                "project_by_id_deleted_owner",
                "project.by.id.deleted.owner@example.com",
                "Project ById Deleted",
                "DEVELOPER"
        );
        String token = loginAndGetToken("project_by_id_deleted_owner", "SecurePass1!");
        long projectId = createProjectAndReturnId(token, ownerId, "Project ById Deleted Case", "Deleted case");

        mockMvc.perform(delete("/projects/{projectId}", projectId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/projects/{projectId}", projectId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/projects/" + projectId));
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
        assertTrue(objectMapper.readTree(result.getResponse().getContentAsString()).size() == 5);
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
        assertTrue(objectMapper.readTree(result.getResponse().getContentAsString()).size() == 4);
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }
}



