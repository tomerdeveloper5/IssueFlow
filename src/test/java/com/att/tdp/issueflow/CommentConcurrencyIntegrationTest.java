package com.att.tdp.issueflow;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.att.tdp.issueflow.domain.Comment;
import com.att.tdp.issueflow.service.CommentService;
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
class CommentConcurrencyIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoSpyBean
    private CommentService commentService;

    @Test
    void commentUpdateWithContentOnlyBodySucceeds() throws Exception {
        String token = registerAndLogin("dev3", "dev3@example.com", "SecurePass1!");
        Long userId = extractUserIdFromMe(token);
        Long projectId = createProject(token, userId);
        Long ticketId = createTicket(token, projectId, userId);
        JsonNode createdComment = createComment(token, ticketId, userId);
        Long commentId = createdComment.get("id").asLong();

        mockMvc.perform(patch("/tickets/{ticketId}/comments/{commentId}", ticketId, commentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "content", "First update"
                        ))))
                .andExpect(status().isOk());
    }

    @Test
    void optimisticLockConflictOnCommentUpdateReturnsConflictStatus() throws Exception {
        String token = registerAndLogin("dev4", "dev4@example.com", "SecurePass1!");
        Long userId = extractUserIdFromMe(token);
        Long projectId = createProject(token, userId);
        Long ticketId = createTicket(token, projectId, userId);
        JsonNode createdComment = createComment(token, ticketId, userId);
        Long commentId = createdComment.get("id").asLong();

        doThrow(new ObjectOptimisticLockingFailureException(Comment.class, commentId))
                .when(commentService)
                .update(eq(ticketId), eq(commentId), any());

        mockMvc.perform(patch("/tickets/{ticketId}/comments/{commentId}", ticketId, commentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "content", "Conflicting update"
                        ))))
                .andExpect(status().isConflict());
    }

    @Test
    void mentionLifecycleAcrossCreateUpdateAndUserMentionsFeed() throws Exception {
        String authorToken = registerAndLogin("devm1", "devm1@example.com", "SecurePass1!");
        String mentionedToken = registerAndLogin("devm2", "devm2@example.com", "SecurePass1!");
        Long authorId = extractUserIdFromMe(authorToken);
        Long mentionedId = extractUserIdFromMe(mentionedToken);
        Long projectId = createProject(authorToken, authorId);
        Long ticketId = createTicket(authorToken, projectId, authorId);

        MvcResult createdResult = mockMvc.perform(post("/tickets/{ticketId}/comments", ticketId)
                        .header("Authorization", "Bearer " + authorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "authorId", authorId,
                                "content", "Initial @devm2 mention"
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        Long commentId = objectMapper.readTree(createdResult.getResponse().getContentAsString()).get("id").asLong();

        MvcResult mentionsBefore = mockMvc.perform(get("/users/{userId}/mentions", mentionedId)
                        .header("Authorization", "Bearer " + authorToken)
                        .param("page", "1")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andReturn();
        assertTrue(objectMapper.readTree(mentionsBefore.getResponse().getContentAsString()).get("total").asLong() >= 1);

        mockMvc.perform(patch("/tickets/{ticketId}/comments/{commentId}", ticketId, commentId)
                        .header("Authorization", "Bearer " + authorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "content", "Mention removed"
                        ))))
                .andExpect(status().isOk());

        MvcResult mentionsAfter = mockMvc.perform(get("/users/{userId}/mentions", mentionedId)
                        .header("Authorization", "Bearer " + authorToken)
                        .param("page", "1")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andReturn();
        assertTrue(objectMapper.readTree(mentionsAfter.getResponse().getContentAsString()).get("total").asLong() == 0);
    }

    private JsonNode createComment(String token, Long ticketId, Long authorId) throws Exception {
        MvcResult result = mockMvc.perform(post("/tickets/{ticketId}/comments", ticketId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "authorId", authorId,
                                "content", "Initial comment"
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode created = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(created.size() == 5);
        assertTrue(!created.has("version"));
        assertTrue(created.has("mentionedUsers"));
        return created;
    }

    private Long createTicket(String token, Long projectId, Long assigneeId) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("title", "Concurrency bug");
        body.put("description", "Need fix");
        body.put("status", "TODO");
        body.put("priority", "MEDIUM");
        body.put("type", "BUG");
        body.put("projectId", projectId);
        body.put("assigneeId", assigneeId);

        MvcResult result = mockMvc.perform(post("/tickets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode createdTicket = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(createdTicket.size() == 10);
        assertTrue(!createdTicket.has("version"));
        return createdTicket.get("id").asLong();
    }

    private Long createProject(String token, Long ownerId) throws Exception {
        MvcResult result = mockMvc.perform(post("/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Project C",
                                "description", "Comment project",
                                "ownerId", ownerId
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private Long extractUserIdFromMe(String token) throws Exception {
        MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private String registerAndLogin(String username, String email, String password) throws Exception {
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "email", email,
                                "fullName", "Developer Three",
                                "role", "DEVELOPER",
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



