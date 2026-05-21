package com.att.tdp.issueflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.att.tdp.issueflow.service.TicketService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HashMap;
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
class ExtendedFeaturesIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TicketService ticketService;

    @Test
    void dependenciesPreventDoneTransitionUntilBlockersResolved() throws Exception {
        UserSession developer = registerAndLogin("DEVELOPER");
        Long projectId = createProject(developer.token, developer.userId);
        Long blockerId = createTicket(developer.token, projectId, developer.userId, "TODO", "HIGH", null, "Blocker");
        Long blockedId = createTicket(developer.token, projectId, developer.userId, "TODO", "MEDIUM", null, "Blocked");

        mockMvc.perform(post("/tickets/{ticketId}/dependencies", blockedId)
                        .header("Authorization", "Bearer " + developer.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("blockedBy", blockerId))))
                .andExpect(status().isOk());

        moveTicketForward(developer.token, blockedId, "IN_PROGRESS");
        moveTicketForward(developer.token, blockedId, "IN_REVIEW");
        mockMvc.perform(patch("/tickets/{ticketId}", blockedId)
                        .header("Authorization", "Bearer " + developer.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "DONE"))))
                .andExpect(status().isBadRequest());

        moveTicketForward(developer.token, blockerId, "IN_PROGRESS");
        moveTicketForward(developer.token, blockerId, "IN_REVIEW");
        moveTicketForward(developer.token, blockerId, "DONE");

        mockMvc.perform(patch("/tickets/{ticketId}", blockedId)
                        .header("Authorization", "Bearer " + developer.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "DONE"))))
                .andExpect(status().isOk());
    }

    @Test
    void supportsAttachmentUploadDeleteAndValidation() throws Exception {
        UserSession developer = registerAndLogin("DEVELOPER");
        Long projectId = createProject(developer.token, developer.userId);
        Long ticketId = createTicket(developer.token, projectId, developer.userId, "TODO", "LOW", null, "Attachment");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "note.txt",
                "text/plain",
                "attachment-data".getBytes(StandardCharsets.UTF_8)
        );

        MvcResult uploadResult = mockMvc.perform(multipart("/tickets/{ticketId}/attachments", ticketId)
                        .file(file)
                        .header("Authorization", "Bearer " + developer.token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode uploaded = objectMapper.readTree(uploadResult.getResponse().getContentAsString());
        Long attachmentId = uploaded.get("id").asLong();
        assertEquals("text/plain", uploaded.get("contentType").asText());

        mockMvc.perform(delete("/tickets/{ticketId}/attachments/{attachmentId}", ticketId, attachmentId)
                        .header("Authorization", "Bearer " + developer.token))
                .andExpect(status().isOk());

        MockMultipartFile badType = new MockMultipartFile("file", "x.exe", "application/octet-stream", new byte[] {1, 2});
        mockMvc.perform(multipart("/tickets/{ticketId}/attachments", ticketId)
                        .file(badType)
                        .header("Authorization", "Bearer " + developer.token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminCanViewAndRestoreDeletedEntities() throws Exception {
        UserSession admin = registerAndLogin("ADMIN");
        UserSession developer = registerAndLogin("DEVELOPER");

        Long projectId = createProject(developer.token, developer.userId);
        Long ticketId = createTicket(developer.token, projectId, developer.userId, "TODO", "MEDIUM", null, "SoftDelete");

        mockMvc.perform(delete("/tickets/{ticketId}", ticketId)
                        .header("Authorization", "Bearer " + developer.token))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/projects/{projectId}", projectId)
                        .header("Authorization", "Bearer " + developer.token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/projects/deleted")
                        .header("Authorization", "Bearer " + developer.token))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/projects/deleted")
                        .header("Authorization", "Bearer " + admin.token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/projects/{projectId}/restore", projectId)
                        .header("Authorization", "Bearer " + admin.token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/tickets/{ticketId}/restore", ticketId)
                        .header("Authorization", "Bearer " + admin.token))
                .andExpect(status().isOk());
    }

    @Test
    void mentionsAreIndexedAndQueryablePerUser() throws Exception {
        UserSession author = registerAndLogin("DEVELOPER");
        UserSession target = registerAndLogin("DEVELOPER");
        Long projectId = createProject(author.token, author.userId);
        Long ticketId = createTicket(author.token, projectId, author.userId, "TODO", "LOW", null, "Mentions");
        String content = "Please review @" + target.username + " now";

        MvcResult createResult = mockMvc.perform(post("/tickets/{ticketId}/comments", ticketId)
                        .header("Authorization", "Bearer " + author.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("authorId", author.userId, "content", content))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode createdComment = objectMapper.readTree(createResult.getResponse().getContentAsString());
        Long commentId = createdComment.get("id").asLong();
        assertObjectKeys(createdComment, "id", "ticketId", "authorId", "content", "mentionedUsers");
        assertTrue(createdComment.get("mentionedUsers").isArray());
        assertEquals(1, createdComment.get("mentionedUsers").size());
        assertObjectKeys(createdComment.get("mentionedUsers").get(0), "id", "username", "fullName");

        MvcResult mentionsResult = mockMvc.perform(get("/users/{userId}/mentions", target.userId)
                        .header("Authorization", "Bearer " + author.token)
                        .param("page", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode mentionPayload = objectMapper.readTree(mentionsResult.getResponse().getContentAsString());
        assertObjectKeys(mentionPayload, "data", "total", "page");
        assertTrue(mentionPayload.get("total").asLong() >= 1);
        assertTrue(mentionPayload.get("data").isArray());
        assertTrue(mentionPayload.get("data").size() >= 1);
        assertObjectKeys(mentionPayload.get("data").get(0), "id", "ticketId", "authorId", "content", "mentionedUsers");

        mockMvc.perform(patch("/tickets/{ticketId}/comments/{commentId}", ticketId, commentId)
                        .header("Authorization", "Bearer " + author.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "Removed mention"))))
                .andExpect(status().isOk());

        MvcResult mentionsAfterUpdate = mockMvc.perform(get("/users/{userId}/mentions", target.userId)
                        .header("Authorization", "Bearer " + author.token)
                        .param("page", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode mentionAfter = objectMapper.readTree(mentionsAfterUpdate.getResponse().getContentAsString());
        assertObjectKeys(mentionAfter, "data", "total", "page");
        assertEquals(0, mentionAfter.get("total").asLong());
        assertTrue(mentionAfter.get("data").isArray());
        assertEquals(0, mentionAfter.get("data").size());
    }

    @Test
    void supportsAutoAssignmentWorkloadImportExportEscalationAndAudit() throws Exception {
        UserSession oldest = registerAndLogin("DEVELOPER");
        UserSession secondDeveloper = registerAndLogin("DEVELOPER");
        Long projectId = createProject(oldest.token, oldest.userId);

        Long firstTicketId = createTicket(oldest.token, projectId, null, "TODO", "LOW", OffsetDateTime.now().minusDays(2), "Auto1");
        Long secondTicketId = createTicket(oldest.token, projectId, null, "TODO", "LOW", null, "Auto2");
        createTicket(oldest.token, projectId, secondDeveloper.userId, "TODO", "LOW", null, "LinkedDeveloper");


        JsonNode firstTicket = getTicket(oldest.token, firstTicketId);
        JsonNode secondTicket = getTicket(oldest.token, secondTicketId);
        assertTrue(firstTicket.hasNonNull("assigneeId"));
        assertTrue(secondTicket.hasNonNull("assigneeId"));

        MvcResult workloadResult = mockMvc.perform(get("/projects/{projectId}/workload", projectId)
                        .header("Authorization", "Bearer " + oldest.token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode workload = objectMapper.readTree(workloadResult.getResponse().getContentAsString());
        assertTrue(workload.isArray());
        assertTrue(workload.size() >= 2);

        MvcResult exportResult = mockMvc.perform(get("/tickets/export").param("projectId", String.valueOf(projectId))
                        .header("Authorization", "Bearer " + oldest.token))
                .andExpect(status().isOk())
                .andReturn();
        String csv = exportResult.getResponse().getContentAsString();
        assertTrue(csv.contains("id,title,description,status,priority,type,assigneeId"));

        String importCsv = "id,title,description,status,priority,type,assigneeId\n"
                + ",Imported title,\"desc,with,commas\",TODO,MEDIUM,BUG,\n";
        MockMultipartFile importFile = new MockMultipartFile(
                "file",
                "tickets.csv",
                "text/csv",
                importCsv.getBytes(StandardCharsets.UTF_8)
        );
        mockMvc.perform(multipart("/tickets/import")
                        .file(importFile)
                        .param("projectId", String.valueOf(projectId))
                        .with(request -> {
                            request.setMethod("POST");
                            return request;
                        })
                        .header("Authorization", "Bearer " + oldest.token))
                .andExpect(status().isOk());

        ticketService.runEscalationCycleNow();
        JsonNode escalated = getTicket(oldest.token, firstTicketId);
        assertEquals("MEDIUM", escalated.get("priority").asText());

        MvcResult auditResult = mockMvc.perform(get("/audit-logs")
                        .header("Authorization", "Bearer " + oldest.token)
                        .param("action", "AUTO_ASSIGN")
                        .param("entityType", "TICKET"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode audit = objectMapper.readTree(auditResult.getResponse().getContentAsString());
        assertTrue(audit.isArray());
        assertTrue(audit.size() >= 2);
    }

    @Test
    void deepCrossFeatureScenarioDependenciesMentionsRestoreWorkloadAndAudit() throws Exception {
        UserSession admin = registerAndLogin("ADMIN");
        UserSession developer = registerAndLogin("DEVELOPER");
        UserSession mentioned = registerAndLogin("DEVELOPER");
        Long projectId = createProject(developer.token, developer.userId);
        Long blockerId = createTicket(developer.token, projectId, developer.userId, "TODO", "HIGH", null, "CrossBlocker");
        Long blockedId = createTicket(developer.token, projectId, null, "TODO", "LOW", OffsetDateTime.now().minusDays(1), "CrossBlocked");

        mockMvc.perform(post("/tickets/{ticketId}/dependencies", blockedId)
                        .header("Authorization", "Bearer " + developer.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("blockedBy", blockerId))))
                .andExpect(status().isOk());

        moveTicketForward(developer.token, blockedId, "IN_PROGRESS");
        moveTicketForward(developer.token, blockedId, "IN_REVIEW");
        mockMvc.perform(patch("/tickets/{ticketId}", blockedId)
                        .header("Authorization", "Bearer " + developer.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "DONE"))))
                .andExpect(status().isBadRequest());

        MvcResult commentCreated = mockMvc.perform(post("/tickets/{ticketId}/comments", blockedId)
                        .header("Authorization", "Bearer " + developer.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "authorId", developer.userId,
                                "content", "Cross flow @" + mentioned.username
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        Long commentId = objectMapper.readTree(commentCreated.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/users/{userId}/mentions", mentioned.userId)
                        .header("Authorization", "Bearer " + developer.token)
                        .param("page", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk());

        moveTicketForward(developer.token, blockerId, "IN_PROGRESS");
        moveTicketForward(developer.token, blockerId, "IN_REVIEW");
        moveTicketForward(developer.token, blockerId, "DONE");
        moveTicketForward(developer.token, blockedId, "DONE");

        mockMvc.perform(delete("/tickets/{ticketId}", blockedId)
                        .header("Authorization", "Bearer " + developer.token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/tickets/{ticketId}/restore", blockedId)
                        .header("Authorization", "Bearer " + admin.token))
                .andExpect(status().isOk());

        ticketService.runEscalationCycleNow();
        mockMvc.perform(get("/projects/{projectId}/workload", projectId)
                        .header("Authorization", "Bearer " + developer.token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/audit-logs")
                        .header("Authorization", "Bearer " + developer.token)
                        .param("entityType", "TICKET"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/tickets/{ticketId}/comments/{commentId}", blockedId, commentId)
                        .header("Authorization", "Bearer " + developer.token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "Cross flow without mention"))))
                .andExpect(status().isOk());
    }

    private void moveTicketForward(String token, Long ticketId, String statusValue) throws Exception {
        mockMvc.perform(patch("/tickets/{ticketId}", ticketId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", statusValue))))
                .andExpect(status().isOk());
    }

    private JsonNode getTicket(String token, Long ticketId) throws Exception {
        MvcResult result = mockMvc.perform(get("/tickets/{ticketId}", ticketId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private Long createProject(String token, Long ownerId) throws Exception {
        MvcResult result = mockMvc.perform(post("/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Project-" + UUID.randomUUID(),
                                "description", "Extended project",
                                "ownerId", ownerId
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private Long createTicket(
            String token,
            Long projectId,
            Long assigneeId,
            String status,
            String priority,
            OffsetDateTime dueDate,
            String title
    ) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title + "-" + UUID.randomUUID());
        body.put("description", "Extended scenario");
        body.put("status", status);
        body.put("priority", priority);
        body.put("type", "BUG");
        body.put("projectId", projectId);
        if (assigneeId != null) {
            body.put("assigneeId", assigneeId);
        }
        if (dueDate != null) {
            body.put("dueDate", dueDate.toString());
        }
        MvcResult result = mockMvc.perform(post("/tickets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private UserSession registerAndLogin(String role) throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = role.toLowerCase() + "_" + suffix;
        String email = username + "@example.com";
        String password = "SecurePass1!";

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "email", email,
                                "fullName", "Sample User",
                                "role", role,
                                "password", password
                        ))))
                .andExpect(status().isOk());

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", password
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(loginResult.getResponse().getContentAsString()).get("accessToken").asText();

        MvcResult meResult = mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        Long userId = objectMapper.readTree(meResult.getResponse().getContentAsString()).get("id").asLong();
        return new UserSession(userId, username, token);
    }

    private record UserSession(Long userId, String username, String token) {
    }

    private void assertObjectKeys(JsonNode objectNode, String... expectedKeys) {
        Set<String> actual = new LinkedHashSet<>();
        objectNode.fieldNames().forEachRemaining(actual::add);
        Set<String> expected = new LinkedHashSet<>(Arrays.asList(expectedKeys));
        assertEquals(expected, actual);
    }
}

