package com.att.tdp.issueflow;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.att.tdp.issueflow.domain.Ticket;
import com.att.tdp.issueflow.service.TicketService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class TicketRulesIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoSpyBean
    private TicketService ticketService;

    @Test
    void ticketStatusMustMoveForwardAndDoneTicketIsImmutable() throws Exception {
        String token = registerAndLogin("dev1", "dev1@example.com", "SecurePass1!", "DEVELOPER");
        Long userId = getUserId(token, "dev1");

        Long projectId = createProject(token, userId);
        JsonNode ticket = createTicket(token, projectId, userId);
        Long ticketId = ticket.get("id").asLong();

        updateTicketStatus(token, ticketId, "IN_PROGRESS");
        updateTicketStatus(token, ticketId, "IN_REVIEW");
        updateTicketStatus(token, ticketId, "DONE");

        mockMvc.perform(patch("/tickets/{ticketId}", ticketId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "status", "IN_PROGRESS"
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void backwardTransitionIsRejected() throws Exception {
        String token = registerAndLogin("dev2", "dev2@example.com", "SecurePass1!", "DEVELOPER");
        Long userId = getUserId(token, "dev2");
        Long projectId = createProject(token, userId);
        JsonNode ticket = createTicket(token, projectId, userId);
        Long ticketId = ticket.get("id").asLong();

        updateTicketStatus(token, ticketId, "IN_PROGRESS");
        mockMvc.perform(patch("/tickets/{ticketId}", ticketId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "status", "TODO"
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void optimisticLockConflictReturnsConflictStatus() throws Exception {
        String token = registerAndLogin("dev3rules", "dev3rules@example.com", "SecurePass1!", "DEVELOPER");
        Long userId = getUserId(token, "dev3rules");
        Long projectId = createProject(token, userId);
        JsonNode ticket = createTicket(token, projectId, userId);
        Long ticketId = ticket.get("id").asLong();

        doThrow(new ObjectOptimisticLockingFailureException(Ticket.class, ticketId))
                .when(ticketService)
                .update(eq(ticketId), any());

        mockMvc.perform(patch("/tickets/{ticketId}", ticketId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Concurrent update"
                        ))))
                .andExpect(status().isConflict());
    }

    @Test
    void dependencyChainBlocksDoneUntilBlockerIsDoneThenAllowsProgress() throws Exception {
        String token = registerAndLogin("devdep", "devdep@example.com", "SecurePass1!", "DEVELOPER");
        Long userId = getUserId(token, "devdep");
        Long projectId = createProject(token, userId);
        Long blockerId = createTicket(token, projectId, userId).get("id").asLong();
        Long blockedId = createTicket(token, projectId, userId).get("id").asLong();

        mockMvc.perform(post("/tickets/{ticketId}/dependencies", blockedId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("blockedBy", blockerId))))
                .andExpect(status().isOk());

        updateTicketStatus(token, blockedId, "IN_PROGRESS");
        updateTicketStatus(token, blockedId, "IN_REVIEW");
        mockMvc.perform(patch("/tickets/{ticketId}", blockedId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "DONE"))))
                .andExpect(status().isBadRequest());

        updateTicketStatus(token, blockerId, "IN_PROGRESS");
        updateTicketStatus(token, blockerId, "IN_REVIEW");
        updateTicketStatus(token, blockerId, "DONE");

        mockMvc.perform(patch("/tickets/{ticketId}", blockedId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "DONE"))))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/tickets/{ticketId}/dependencies/{blockerId}", blockedId, blockerId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private void updateTicketStatus(String token, Long ticketId, String status) throws Exception {
        mockMvc.perform(patch("/tickets/{ticketId}", ticketId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "status", status
                        ))))
                .andExpect(status().isOk());
    }

    private JsonNode createTicket(String token, Long projectId, Long assigneeId) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("title", "Fix login bug");
        body.put("description", "Something breaks");
        body.put("status", "TODO");
        body.put("priority", "HIGH");
        body.put("type", "BUG");
        body.put("projectId", projectId);
        body.put("assigneeId", assigneeId);

        MvcResult result = mockMvc.perform(post("/tickets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode created = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(created.size() == 10);
        assertTrue(!created.has("version"));
        return created;
    }

    private Long createProject(String token, Long ownerId) throws Exception {
        MvcResult result = mockMvc.perform(post("/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Project A",
                                "description", "Core project",
                                "ownerId", ownerId
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private Long getUserId(String token, String username) throws Exception {
        MvcResult result = mockMvc.perform(get("/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode users = objectMapper.readTree(result.getResponse().getContentAsString());
        for (JsonNode user : users) {
            if (username.equals(user.get("username").asText())) {
                return user.get("id").asLong();
            }
        }
        throw new IllegalStateException("Unable to find user id");
    }

    private String registerAndLogin(String username, String email, String password, String role) throws Exception {
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "email", email,
                                "fullName", "Developer",
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
        return objectMapper.readTree(loginResult.getResponse().getContentAsString()).get("accessToken").asText();
    }
}



