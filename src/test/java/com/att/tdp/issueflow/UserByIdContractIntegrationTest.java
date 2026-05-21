package com.att.tdp.issueflow;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
class UserByIdContractIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${security.jwt.secret}")
    private String jwtSecret;

    @Test
    void getUserByIdWithValidTokenReturnsExpectedFieldsAndValues() throws Exception {
        long createdUserId = registerUserAndReturnId(
                "user_by_id_contract_1",
                "user.by.id.contract.1@example.com",
                "User One",
                "DEVELOPER"
        );
        String token = loginAndGetToken("user_by_id_contract_1", "SecurePass1!");

        MvcResult result = mockMvc.perform(get("/users/{userId}", createdUserId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode user = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(user.size() == 5);
        assertTrue(user.get("id").asLong() == createdUserId);
        assertTrue("user_by_id_contract_1".equals(user.get("username").asText()));
        assertTrue("user.by.id.contract.1@example.com".equals(user.get("email").asText()));
        assertTrue("User One".equals(user.get("fullName").asText()));
        assertTrue("DEVELOPER".equals(user.get("role").asText()));
        assertFalse(user.has("password"));
        assertFalse(user.has("passwordHash"));
    }

    @Test
    void getUserByIdWithoutTokenReturnsAuthRequired() throws Exception {
        mockMvc.perform(get("/users/{userId}", 1L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_REQUIRED"))
                .andExpect(jsonPath("$.path").value("/users/1"))
                .andExpect(jsonPath("$.explanation").isNotEmpty())
                .andExpect(jsonPath("$.action").isNotEmpty());
    }

    @Test
    void getUserByIdWithInvalidTokenReturnsAuthInvalidToken() throws Exception {
        mockMvc.perform(get("/users/{userId}", 1L)
                        .header("Authorization", "Bearer invalid.token.value"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_INVALID_TOKEN"))
                .andExpect(jsonPath("$.path").value("/users/1"))
                .andExpect(jsonPath("$.explanation").isNotEmpty())
                .andExpect(jsonPath("$.action").isNotEmpty());
    }

    @Test
    void getUserByIdWithExpiredTokenReturnsAuthTokenExpired() throws Exception {
        long userId = registerUserAndReturnId(
                "user_by_id_expired",
                "user.by.id.expired@example.com",
                "Expired User",
                "DEVELOPER"
        );
        String expiredToken = createExpiredToken("user_by_id_expired");

        mockMvc.perform(get("/users/{userId}", userId)
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_TOKEN_EXPIRED"))
                .andExpect(jsonPath("$.path").value("/users/" + userId))
                .andExpect(jsonPath("$.explanation").isNotEmpty())
                .andExpect(jsonPath("$.action").isNotEmpty());
    }

    @Test
    void getUserByIdWithLoggedOutTokenReturnsAuthLoggedOutToken() throws Exception {
        long userId = registerUserAndReturnId(
                "user_by_id_logout",
                "user.by.id.logout@example.com",
                "Logout User",
                "DEVELOPER"
        );
        String token = loginAndGetToken("user_by_id_logout", "SecurePass1!");

        mockMvc.perform(post("/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/users/{userId}", userId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_LOGGED_OUT_TOKEN"))
                .andExpect(jsonPath("$.path").value("/users/" + userId))
                .andExpect(jsonPath("$.explanation").isNotEmpty())
                .andExpect(jsonPath("$.action").isNotEmpty());
    }

    @Test
    void getUserByIdWithValidTokenAndUnknownUserReturnsNotFound() throws Exception {
        registerUserAndReturnId(
                "user_by_id_not_found",
                "user.by.id.not.found@example.com",
                "Not Found User",
                "DEVELOPER"
        );
        String token = loginAndGetToken("user_by_id_not_found", "SecurePass1!");

        mockMvc.perform(get("/users/{userId}", 999999999L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/users/999999999"))
                .andExpect(jsonPath("$.explanation").isNotEmpty())
                .andExpect(jsonPath("$.action").isNotEmpty());
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
}



