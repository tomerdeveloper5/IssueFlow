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
    void supportsAutoAssignmentWorkloadImportExportEscalationAndAudit() throws Exception {
        UserSession oldest = registerAndLogin("DEVELOPER");
        registerAndLogin("DEVELOPER");
        Long projectId = createProject(oldest.token, oldest.userId);

        Long firstTicketId = createTicket(oldest.token, projectId, null, "TODO", "LOW", OffsetDateTime.now().minusDays(2), "Auto1");
        Long secondTicketId = createTicket(oldest.token, projectId, null, "TODO", "LOW", null, "Auto2");

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
    void leavesTicketUnassignedWhenProjectHasNoLinkedDevelopers() throws Exception {
        UserSession adminOwner = registerAndLogin("ADMIN");
        registerAndLogin("DEVELOPER");
        Long projectId = createProject(adminOwner.token, adminOwner.userId);

        Long ticketId = createTicket(adminOwner.token, projectId, null, "TODO", "LOW", null, "NoLinkedDevelopers");
        JsonNode createdTicket = getTicket(adminOwner.token, ticketId);
        assertTrue(createdTicket.has("assigneeId"));
        assertTrue(createdTicket.get("assigneeId").isNull());
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

