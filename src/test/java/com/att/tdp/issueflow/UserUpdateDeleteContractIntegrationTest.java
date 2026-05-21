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
class UserUpdateDeleteContractIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${security.jwt.secret}")
    private String jwtSecret;

    @Test
    void updateUserWithValidTokenUpdatesFieldsAndKeepsIdentityFields() throws Exception {
        long userId = registerUserAndReturnId(
                "update_contract_user",
                "update.contract.user@example.com",
                "Before Update",
                "DEVELOPER"
        );
        String token = loginAndGetToken("update_contract_user", "SecurePass1!");

        mockMvc.perform(post("/users/update/{userId}", userId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "After Update",
                                "role", "ADMIN"
                        ))))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/users/{userId}", userId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode user = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(user.get("id").asLong() == userId);
        assertTrue("update_contract_user".equals(user.get("username").asText()));
        assertTrue("update.contract.user@example.com".equals(user.get("email").asText()));
        assertTrue("After Update".equals(user.get("fullName").asText()));
        assertTrue("ADMIN".equals(user.get("role").asText()));
    }

    @Test
    void deleteUserWithValidTokenActuallyDeletesUser() throws Exception {
        long userIdToDelete = registerUserAndReturnId(
                "delete_contract_target_user",
                "delete.contract.target.user@example.com",
                "Delete Target User",
                "DEVELOPER"
        );
        registerUserAndReturnId(
                "delete_contract_actor_user",
                "delete.contract.actor.user@example.com",
                "Delete Actor User",
                "DEVELOPER"
        );
        String actorToken = loginAndGetToken("delete_contract_actor_user", "SecurePass1!");

        mockMvc.perform(delete("/users/{userId}", userIdToDelete)
                        .header("Authorization", "Bearer " + actorToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/users/{userId}", userIdToDelete)
                        .header("Authorization", "Bearer " + actorToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void updateUserValidationFailureReturnsBadRequest() throws Exception {
        long userId = registerUserAndReturnId(
                "update_validation_user",
                "update.validation.user@example.com",
                "Validation User",
                "DEVELOPER"
        );
        String token = loginAndGetToken("update_validation_user", "SecurePass1!");

        mockMvc.perform(post("/users/update/{userId}", userId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "!",
                                "role", "ADMIN"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.validationErrors.fullName").exists());
    }

    @Test
    void updateUnknownUserReturnsNotFound() throws Exception {
        registerUserAndReturnId(
                "update_not_found_user",
                "update.not.found.user@example.com",
                "Not Found User",
                "DEVELOPER"
        );
        String token = loginAndGetToken("update_not_found_user", "SecurePass1!");

        mockMvc.perform(post("/users/update/{userId}", 999999999L)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Updated Name",
                                "role", "ADMIN"
                        ))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/users/update/999999999"));
    }

    @Test
    void deleteUnknownUserReturnsNotFound() throws Exception {
        registerUserAndReturnId(
                "delete_not_found_user",
                "delete.not.found.user@example.com",
                "Not Found User",
                "DEVELOPER"
        );
        String token = loginAndGetToken("delete_not_found_user", "SecurePass1!");

        mockMvc.perform(delete("/users/{userId}", 999999999L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/users/999999999"));
    }

    @Test
    void updateUserWithoutTokenReturnsAuthRequired() throws Exception {
        mockMvc.perform(post("/users/update/{userId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "No Auth",
                                "role", "ADMIN"
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_REQUIRED"));
    }

    @Test
    void updateUserWithInvalidTokenReturnsAuthInvalidToken() throws Exception {
        mockMvc.perform(post("/users/update/{userId}", 1L)
                        .header("Authorization", "Bearer invalid.token.value")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Invalid Token",
                                "role", "ADMIN"
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_INVALID_TOKEN"));
    }

    @Test
    void updateUserWithExpiredTokenReturnsAuthTokenExpired() throws Exception {
        registerUserAndReturnId(
                "update_expired_user",
                "update.expired.user@example.com",
                "Expired User",
                "DEVELOPER"
        );
        String expiredToken = createExpiredToken("update_expired_user");

        mockMvc.perform(post("/users/update/{userId}", 1L)
                        .header("Authorization", "Bearer " + expiredToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Expired",
                                "role", "ADMIN"
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_TOKEN_EXPIRED"));
    }

    @Test
    void updateUserWithLoggedOutTokenReturnsAuthLoggedOutToken() throws Exception {
        long userId = registerUserAndReturnId(
                "update_logout_user",
                "update.logout.user@example.com",
                "Logout User",
                "DEVELOPER"
        );
        String token = loginAndGetToken("update_logout_user", "SecurePass1!");

        mockMvc.perform(post("/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/users/update/{userId}", userId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "No Longer Valid",
                                "role", "ADMIN"
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_LOGGED_OUT_TOKEN"));
    }

    @Test
    void deleteUserWithoutTokenReturnsAuthRequired() throws Exception {
        mockMvc.perform(delete("/users/{userId}", 1L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_REQUIRED"));
    }

    @Test
    void deleteUserWithInvalidTokenReturnsAuthInvalidToken() throws Exception {
        mockMvc.perform(delete("/users/{userId}", 1L)
                        .header("Authorization", "Bearer invalid.token.value"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_INVALID_TOKEN"));
    }

    @Test
    void deleteUserWithExpiredTokenReturnsAuthTokenExpired() throws Exception {
        registerUserAndReturnId(
                "delete_expired_user",
                "delete.expired.user@example.com",
                "Expired User",
                "DEVELOPER"
        );
        String expiredToken = createExpiredToken("delete_expired_user");

        mockMvc.perform(delete("/users/{userId}", 1L)
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_TOKEN_EXPIRED"));
    }

    @Test
    void deleteUserWithLoggedOutTokenReturnsAuthLoggedOutToken() throws Exception {
        long userId = registerUserAndReturnId(
                "delete_logout_user",
                "delete.logout.user@example.com",
                "Logout User",
                "DEVELOPER"
        );
        String token = loginAndGetToken("delete_logout_user", "SecurePass1!");

        mockMvc.perform(post("/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/users/{userId}", userId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_LOGGED_OUT_TOKEN"));
    }

    @Test
    void updateThenDeleteThenVerifyGoneInUsersList() throws Exception {
        long actorId = registerUserAndReturnId(
                "update_delete_chain_actor",
                "update.delete.chain.actor@example.com",
                "Chain Actor",
                "DEVELOPER"
        );
        String actorToken = loginAndGetToken("update_delete_chain_actor", "SecurePass1!");
        long targetId = registerUserAndReturnId(
                "update_delete_chain_target",
                "update.delete.chain.target@example.com",
                "Chain Target",
                "DEVELOPER"
        );

        mockMvc.perform(post("/users/update/{userId}", targetId)
                        .header("Authorization", "Bearer " + actorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Chain Target Updated",
                                "role", "ADMIN"
                        ))))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/users/{userId}", targetId)
                        .header("Authorization", "Bearer " + actorToken))
                .andExpect(status().isOk());

        MvcResult usersResult = mockMvc.perform(get("/users")
                        .header("Authorization", "Bearer " + actorToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode users = objectMapper.readTree(usersResult.getResponse().getContentAsString());
        boolean exists = false;
        for (JsonNode user : users) {
            if (user.get("id").asLong() == targetId) {
                exists = true;
                break;
            }
        }
        assertTrue(!exists);
        assertTrue(actorId > 0);
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
}



