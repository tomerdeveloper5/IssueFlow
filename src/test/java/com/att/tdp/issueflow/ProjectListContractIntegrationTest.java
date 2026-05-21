package com.att.tdp.issueflow;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
class ProjectListContractIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${security.jwt.secret}")
    private String jwtSecret;

    @Test
    void getProjectsWithValidTokenReturnsOnlyActiveProjectFields() throws Exception {
        long ownerId = registerUserAndReturnId(
                "project_list_owner_active",
                "project.list.owner.active@example.com",
                "Project List Owner Active",
                "DEVELOPER"
        );
        String token = loginAndGetToken("project_list_owner_active", "SecurePass1!");
        long activeProjectId = createProjectAndReturnId(token, ownerId, "Projects List Active", "Active description");
        long deletedProjectId = createProjectAndReturnId(token, ownerId, "Projects List Deleted", "Deleted description");

        mockMvc.perform(delete("/projects/{projectId}", deletedProjectId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/projects")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode projects = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode activeProject = findProjectByName(projects, "Projects List Active");
        JsonNode softDeletedProject = findProjectByName(projects, "Projects List Deleted");

        assertNotNull(activeProject);
        assertTrue(activeProject.size() == 4);
        assertTrue(activeProject.get("id").asLong() == activeProjectId);
        assertTrue("Active description".equals(activeProject.get("description").asText()));
        assertTrue(activeProject.get("ownerId").asLong() == ownerId);
        assertNull(softDeletedProject);
    }

    @Test
    void getProjectsWithoutTokenReturnsAuthRequired() throws Exception {
        mockMvc.perform(get("/projects"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_REQUIRED"))
                .andExpect(jsonPath("$.path").value("/projects"))
                .andExpect(jsonPath("$.explanation").isNotEmpty())
                .andExpect(jsonPath("$.action").isNotEmpty());
    }

    @Test
    void getProjectsWithInvalidTokenReturnsAuthInvalidToken() throws Exception {
        mockMvc.perform(get("/projects")
                        .header("Authorization", "Bearer invalid.token.value"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_INVALID_TOKEN"))
                .andExpect(jsonPath("$.path").value("/projects"))
                .andExpect(jsonPath("$.explanation").isNotEmpty())
                .andExpect(jsonPath("$.action").isNotEmpty());
    }

    @Test
    void getProjectsWithExpiredTokenReturnsAuthTokenExpired() throws Exception {
        registerUserAndReturnId(
                "project_list_expired_user",
                "project.list.expired.user@example.com",
                "Project List Expired User",
                "DEVELOPER"
        );
        String expiredToken = createExpiredToken("project_list_expired_user");

        mockMvc.perform(get("/projects")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_TOKEN_EXPIRED"))
                .andExpect(jsonPath("$.path").value("/projects"))
                .andExpect(jsonPath("$.explanation").isNotEmpty())
                .andExpect(jsonPath("$.action").isNotEmpty());
    }

    @Test
    void getProjectsWithLoggedOutTokenReturnsAuthLoggedOutToken() throws Exception {
        registerUserAndReturnId(
                "project_list_logout_user",
                "project.list.logout.user@example.com",
                "Project List Logout User",
                "DEVELOPER"
        );
        String token = loginAndGetToken("project_list_logout_user", "SecurePass1!");

        mockMvc.perform(post("/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/projects")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_LOGGED_OUT_TOKEN"))
                .andExpect(jsonPath("$.path").value("/projects"))
                .andExpect(jsonPath("$.explanation").isNotEmpty())
                .andExpect(jsonPath("$.action").isNotEmpty());
    }

    @Test
    void projectListChainWithDeletedAndRestoreFlow() throws Exception {
        long ownerId = registerUserAndReturnId(
                "project_list_chain_owner",
                "project.list.chain.owner@example.com",
                "Project Chain Owner",
                "DEVELOPER"
        );
        registerUserAndReturnId(
                "project_list_chain_admin",
                "project.list.chain.admin@example.com",
                "Project Chain Admin",
                "ADMIN"
        );
        String ownerToken = loginAndGetToken("project_list_chain_owner", "SecurePass1!");
        String adminToken = loginAndGetToken("project_list_chain_admin", "SecurePass1!");
        long projectId = createProjectAndReturnId(ownerToken, ownerId, "Project Chain", "Project chain description");

        mockMvc.perform(delete("/projects/{projectId}", projectId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/projects/{projectId}", projectId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/projects/{projectId}/restore", projectId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/projects/{projectId}", projectId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Project Chain"));
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

    private JsonNode findProjectByName(JsonNode projects, String name) {
        for (JsonNode project : projects) {
            if (name.equals(project.get("name").asText())) {
                return project;
            }
        }
        return null;
    }
}



