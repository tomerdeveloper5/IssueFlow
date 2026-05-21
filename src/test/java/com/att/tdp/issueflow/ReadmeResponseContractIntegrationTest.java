package com.att.tdp.issueflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class ReadmeResponseContractIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void readme200ResponseShapesMatchContract() throws Exception {
        UserSession developer = registerAndLogin("DEVELOPER");
        UserSession admin = registerAndLogin("ADMIN");
        UserSession secondDeveloper = registerAndLogin("DEVELOPER");

        // Users API
        JsonNode usersList = responseJson(mockMvc.perform(get("/users")
                .header("Authorization", "Bearer " + developer.token))
                .andExpect(status().isOk())
                .andReturn());
        assertTrue(usersList.isArray());
        assertTrue(usersList.size() >= 1);
        assertObjectKeys(usersList.get(0), "id", "username", "email", "fullName", "role");

        JsonNode userById = responseJson(mockMvc.perform(get("/users/{userId}", developer.userId)
                .header("Authorization", "Bearer " + developer.token))
                .andExpect(status().isOk())
                .andReturn());
        assertObjectKeys(userById, "id", "username", "email", "fullName", "role");

        mockMvc.perform(post("/users/update/{userId}", secondDeveloper.userId)
                        .header("Authorization", "Bearer " + developer.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Second Developer Updated",
                                "role", "DEVELOPER"
                        ))))
                .andExpect(status().isOk());

        // Auth API
        JsonNode me = responseJson(mockMvc.perform(get("/auth/me")
                .header("Authorization", "Bearer " + developer.token))
                .andExpect(status().isOk())
                .andReturn());
        assertObjectKeys(me, "id", "username", "email", "fullName", "role");

        // Projects API
        Long projectId = createProject(developer.token, developer.userId);
        JsonNode projects = responseJson(mockMvc.perform(get("/projects")
                .header("Authorization", "Bearer " + developer.token))
                .andExpect(status().isOk())
                .andReturn());
        assertTrue(projects.isArray());
        assertTrue(projects.size() >= 1);
        assertObjectKeys(projects.get(0), "id", "name", "description", "ownerId");

        JsonNode projectById = responseJson(mockMvc.perform(get("/projects/{projectId}", projectId)
                .header("Authorization", "Bearer " + developer.token))
                .andExpect(status().isOk())
                .andReturn());
        assertObjectKeys(projectById, "id", "name", "description", "ownerId");

        // Tickets API
        Long blockerId = createTicket(developer.token, projectId, developer.userId, "Blocker", null);
        Long blockedId = createTicket(developer.token, projectId, developer.userId, "Blocked", OffsetDateTime.now().plusDays(1));

        JsonNode ticketsByProject = responseJson(mockMvc.perform(get("/tickets")
                .header("Authorization", "Bearer " + developer.token)
                .param("projectId", String.valueOf(projectId)))
                .andExpect(status().isOk())
                .andReturn());
        assertTrue(ticketsByProject.isArray());
        assertTrue(ticketsByProject.size() >= 1);
        assertObjectKeys(
                ticketsByProject.get(0),
                "id", "title", "description", "status", "priority", "type", "projectId", "assigneeId", "dueDate", "isOverdue"
        );

        JsonNode ticketById = responseJson(mockMvc.perform(get("/tickets/{ticketId}", blockerId)
                .header("Authorization", "Bearer " + developer.token))
                .andExpect(status().isOk())
                .andReturn());
        assertObjectKeys(
                ticketById,
                "id", "title", "description", "status", "priority", "type", "projectId", "assigneeId", "dueDate", "isOverdue"
        );

        mockMvc.perform(patch("/tickets/{ticketId}", blockerId)
                        .header("Authorization", "Bearer " + developer.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "IN_PROGRESS"))))
                .andExpect(status().isOk());

        // Dependencies API
        mockMvc.perform(post("/tickets/{ticketId}/dependencies", blockedId)
                        .header("Authorization", "Bearer " + developer.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("blockedBy", blockerId))))
                .andExpect(status().isOk());

        JsonNode dependencies = responseJson(mockMvc.perform(get("/tickets/{ticketId}/dependencies", blockedId)
                .header("Authorization", "Bearer " + developer.token))
                .andExpect(status().isOk())
                .andReturn());
        assertTrue(dependencies.isArray());
        assertTrue(dependencies.size() >= 1);
        assertObjectKeys(dependencies.get(0), "id", "title", "status");

        // Comments + Mentions APIs
        JsonNode createdComment = responseJson(mockMvc.perform(post("/tickets/{ticketId}/comments", blockerId)
                .header("Authorization", "Bearer " + developer.token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "authorId", developer.userId,
                        "content", "Hello @" + secondDeveloper.username
                ))))
                .andExpect(status().isOk())
                .andReturn());
        Long commentId = createdComment.get("id").asLong();
        assertObjectKeys(createdComment, "id", "ticketId", "authorId", "content", "mentionedUsers");
        assertTrue(createdComment.get("mentionedUsers").isArray());
        assertTrue(createdComment.get("mentionedUsers").size() >= 1);
        assertObjectKeys(createdComment.get("mentionedUsers").get(0), "id", "username", "fullName");

        JsonNode comments = responseJson(mockMvc.perform(get("/tickets/{ticketId}/comments", blockerId)
                .header("Authorization", "Bearer " + developer.token))
                .andExpect(status().isOk())
                .andReturn());
        assertTrue(comments.isArray());
        assertTrue(comments.size() >= 1);
        assertObjectKeys(comments.get(0), "id", "ticketId", "authorId", "content", "mentionedUsers");

        JsonNode mentions = responseJson(mockMvc.perform(get("/users/{userId}/mentions", secondDeveloper.userId)
                .header("Authorization", "Bearer " + developer.token)
                .param("page", "1")
                .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andReturn());
        assertObjectKeys(mentions, "data", "total", "page");
        assertTrue(mentions.get("data").isArray());
        assertTrue(mentions.get("data").size() >= 1);
        assertObjectKeys(mentions.get("data").get(0), "id", "ticketId", "authorId", "content", "mentionedUsers");

        mockMvc.perform(patch("/tickets/{ticketId}/comments/{commentId}", blockerId, commentId)
                        .header("Authorization", "Bearer " + developer.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "Updated content"))))
                .andExpect(status().isOk());

        // Attachments API
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "contract.txt",
                "text/plain",
                "hello".getBytes(StandardCharsets.UTF_8)
        );
        JsonNode attachment = responseJson(mockMvc.perform(multipart("/tickets/{ticketId}/attachments", blockerId)
                .file(file)
                .header("Authorization", "Bearer " + developer.token))
                .andExpect(status().isOk())
                .andReturn());
        Long attachmentId = attachment.get("id").asLong();
        assertObjectKeys(attachment, "id", "ticketId", "filename", "contentType");

        mockMvc.perform(delete("/tickets/{ticketId}/attachments/{attachmentId}", blockerId, attachmentId)
                        .header("Authorization", "Bearer " + developer.token))
                .andExpect(status().isOk());

        // Workload API
        JsonNode workload = responseJson(mockMvc.perform(get("/projects/{projectId}/workload", projectId)
                .header("Authorization", "Bearer " + developer.token))
                .andExpect(status().isOk())
                .andReturn());
        assertTrue(workload.isArray());
        assertTrue(workload.size() >= 1);
        assertObjectKeys(workload.get(0), "userId", "username", "openTicketCount");

        // Export / Import API
        MvcResult exportResult = mockMvc.perform(get("/tickets/export")
                        .header("Authorization", "Bearer " + developer.token)
                        .param("projectId", String.valueOf(projectId)))
                .andExpect(status().isOk())
                .andReturn();
        String csv = exportResult.getResponse().getContentAsString();
        assertTrue(csv.contains("id,title,description,status,priority,type,assigneeId"));

        String csvPayload = "id,title,description,status,priority,type,assigneeId\n"
                + ",Imported,Sample TODO,TODO,MEDIUM,BUG,\n";
        MockMultipartFile importFile = new MockMultipartFile(
                "file",
                "import.csv",
                "text/csv",
                csvPayload.getBytes(StandardCharsets.UTF_8)
        );
        JsonNode importResponse = responseJson(mockMvc.perform(multipart("/tickets/import")
                        .file(importFile)
                        .param("projectId", String.valueOf(projectId))
                        .with(request -> {
                            request.setMethod("POST");
                            return request;
                        })
                        .header("Authorization", "Bearer " + developer.token))
                .andExpect(status().isOk())
                .andReturn());
        assertObjectKeys(importResponse, "created", "failed", "errors");

        // Soft delete APIs (ticket, then project)
        mockMvc.perform(delete("/tickets/{ticketId}", blockerId)
                        .header("Authorization", "Bearer " + developer.token))
                .andExpect(status().isOk());

        JsonNode deletedTickets = responseJson(mockMvc.perform(get("/tickets/deleted")
                .header("Authorization", "Bearer " + admin.token)
                .param("projectId", String.valueOf(projectId)))
                .andExpect(status().isOk())
                .andReturn());
        assertTrue(deletedTickets.isArray());
        assertTrue(deletedTickets.size() >= 1);
        assertObjectKeys(deletedTickets.get(0), "id", "title", "status", "priority", "type", "projectId");

        mockMvc.perform(post("/tickets/{ticketId}/restore", blockerId)
                        .header("Authorization", "Bearer " + admin.token))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/projects/{projectId}", projectId)
                        .header("Authorization", "Bearer " + developer.token))
                .andExpect(status().isOk());

        JsonNode deletedProjects = responseJson(mockMvc.perform(get("/projects/deleted")
                .header("Authorization", "Bearer " + admin.token))
                .andExpect(status().isOk())
                .andReturn());
        assertTrue(deletedProjects.isArray());
        assertTrue(deletedProjects.size() >= 1);
        assertObjectKeys(deletedProjects.get(0), "id", "name", "description", "ownerId");

        mockMvc.perform(post("/projects/{projectId}/restore", projectId)
                        .header("Authorization", "Bearer " + admin.token))
                .andExpect(status().isOk());

        // Audit API
        JsonNode auditLogs = responseJson(mockMvc.perform(get("/audit-logs")
                .header("Authorization", "Bearer " + developer.token))
                .andExpect(status().isOk())
                .andReturn());
        assertTrue(auditLogs.isArray());
        assertTrue(auditLogs.size() >= 1);
        assertObjectKeys(auditLogs.get(0), "id", "action", "entityType", "entityId", "performedBy", "actor", "timestamp");

        // Remaining status-only endpoints with blank/unspecified body
        mockMvc.perform(delete("/tickets/{ticketId}/dependencies/{blockerId}", blockedId, blockerId)
                        .header("Authorization", "Bearer " + developer.token))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/tickets/{ticketId}/comments/{commentId}", blockerId, commentId)
                        .header("Authorization", "Bearer " + developer.token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/auth/logout")
                        .header("Authorization", "Bearer " + developer.token))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/users/{userId}", secondDeveloper.userId)
                        .header("Authorization", "Bearer " + admin.token))
                .andExpect(status().isOk());
    }

    @Test
    void non200ContractChecksStatusOnly() throws Exception {
        UserSession developer = registerAndLogin("DEVELOPER");
        Long projectId = createProject(developer.token, developer.userId);
        Long ticketId = createTicket(developer.token, projectId, developer.userId, "Contract-Forbidden", null);

        // README does not define non-200 payload schema -> status-only assertion
        mockMvc.perform(post("/tickets/{ticketId}/restore", ticketId)
                        .header("Authorization", "Bearer " + developer.token))
                .andExpect(status().isForbidden());
    }

    private Long createProject(String token, Long ownerId) throws Exception {
        JsonNode response = responseJson(mockMvc.perform(post("/projects")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "name", "Contract-Project-" + UUID.randomUUID(),
                        "description", "README contract project",
                        "ownerId", ownerId
                ))))
                .andExpect(status().isOk())
                .andReturn());
        assertObjectKeys(response, "id", "name", "description", "ownerId");
        return response.get("id").asLong();
    }

    private Long createTicket(String token, Long projectId, Long assigneeId, String title, OffsetDateTime dueDate) throws Exception {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("title", title + "-" + UUID.randomUUID());
        payload.put("description", "README contract ticket");
        payload.put("status", "TODO");
        payload.put("priority", "HIGH");
        payload.put("type", "BUG");
        payload.put("projectId", projectId);
        payload.put("assigneeId", assigneeId);
        if (dueDate != null) {
            payload.put("dueDate", dueDate.toString());
        }

        JsonNode response = responseJson(mockMvc.perform(post("/tickets")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andReturn());
        assertObjectKeys(
                response,
                "id", "title", "description", "status", "priority", "type", "projectId", "assigneeId", "dueDate", "isOverdue"
        );
        return response.get("id").asLong();
    }

    private UserSession registerAndLogin(String role) throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = role.toLowerCase() + "_contract_" + suffix;
        String email = username + "@example.com";

        JsonNode createdUser = responseJson(mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "username", username,
                        "email", email,
                        "fullName", "Contract User",
                        "role", role,
                        "password", "SecurePass1!"
                ))))
                .andExpect(status().isOk())
                .andReturn());
        assertObjectKeys(createdUser, "id", "username", "email", "fullName", "role");

        JsonNode loginResponse = responseJson(mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "username", username,
                        "password", "SecurePass1!"
                ))))
                .andExpect(status().isOk())
                .andReturn());
        assertObjectKeys(loginResponse, "accessToken", "tokenType", "expiresIn");
        return new UserSession(createdUser.get("id").asLong(), username, loginResponse.get("accessToken").asText());
    }

    private JsonNode responseJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private void assertObjectKeys(JsonNode objectNode, String... expectedKeys) {
        Set<String> actual = new LinkedHashSet<>();
        objectNode.fieldNames().forEachRemaining(actual::add);
        Set<String> expected = new LinkedHashSet<>(Arrays.asList(expectedKeys));
        assertEquals(expected, actual);
    }

    private record UserSession(Long userId, String username, String token) {
    }
}

