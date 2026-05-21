package com.att.tdp.issueflow;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
class UserListContractIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${security.jwt.secret}")
    private String jwtSecret;

    @Test
    void getUsersWithValidTokenReturnsExpectedUserFields() throws Exception {
        registerUser("users_contract_dev1", "users.contract.dev1@example.com", "Developer One", "DEVELOPER");
        registerUser("users_contract_dev2", "users.contract.dev2@example.com", "Developer Two", "DEVELOPER");
        String token = loginAndGetToken("users_contract_dev1", "SecurePass1!");

        MvcResult result = mockMvc.perform(get("/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode users = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode firstUser = findUserByUsername(users, "users_contract_dev1");
        JsonNode secondUser = findUserByUsername(users, "users_contract_dev2");

        assertNotNull(firstUser);
        assertNotNull(secondUser);
        assertTrue(firstUser.size() == 5);
        assertTrue(secondUser.size() == 5);
        assertTrue(firstUser.get("id").isNumber());
        assertTrue("users.contract.dev1@example.com".equals(firstUser.get("email").asText()));
        assertTrue("Developer One".equals(firstUser.get("fullName").asText()));
        assertTrue("DEVELOPER".equals(firstUser.get("role").asText()));
        assertFalse(firstUser.has("passwordHash"));
        assertFalse(firstUser.has("password"));
    }

    @Test
    void getUsersWithoutTokenReturnsAuthRequired() throws Exception {
        mockMvc.perform(get("/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_REQUIRED"))
                .andExpect(jsonPath("$.path").value("/users"))
                .andExpect(jsonPath("$.explanation").isNotEmpty())
                .andExpect(jsonPath("$.action").isNotEmpty());
    }

    @Test
    void getUsersWithInvalidTokenReturnsAuthInvalidToken() throws Exception {
        mockMvc.perform(get("/users")
                        .header("Authorization", "Bearer invalid.token.value"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_INVALID_TOKEN"))
                .andExpect(jsonPath("$.path").value("/users"))
                .andExpect(jsonPath("$.explanation").isNotEmpty())
                .andExpect(jsonPath("$.action").isNotEmpty());
    }

    @Test
    void getUsersWithExpiredTokenReturnsAuthTokenExpired() throws Exception {
        registerUser("users_contract_expired", "users.contract.expired@example.com", "Expired User", "DEVELOPER");
        String expiredToken = createExpiredToken("users_contract_expired");

        mockMvc.perform(get("/users")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_TOKEN_EXPIRED"))
                .andExpect(jsonPath("$.path").value("/users"))
                .andExpect(jsonPath("$.explanation").isNotEmpty())
                .andExpect(jsonPath("$.action").isNotEmpty());
    }

    @Test
    void getUsersWithLoggedOutTokenReturnsAuthLoggedOutToken() throws Exception {
        registerUser("users_contract_logout", "users.contract.logout@example.com", "Logout User", "DEVELOPER");
        String token = loginAndGetToken("users_contract_logout", "SecurePass1!");

        mockMvc.perform(post("/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_LOGGED_OUT_TOKEN"))
                .andExpect(jsonPath("$.path").value("/users"))
                .andExpect(jsonPath("$.explanation").isNotEmpty())
                .andExpect(jsonPath("$.action").isNotEmpty());
    }

    @Test
    void userListReflectsCreateAndRemovesDeletedUserInSameFlow() throws Exception {
        registerUser("users_chain_actor", "users.chain.actor@example.com", "Users Chain Actor", "DEVELOPER");
        String token = loginAndGetToken("users_chain_actor", "SecurePass1!");
        registerUser("users_chain_target", "users.chain.target@example.com", "Users Chain Target", "DEVELOPER");

        MvcResult beforeDeleteResult = mockMvc.perform(get("/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode beforeDelete = objectMapper.readTree(beforeDeleteResult.getResponse().getContentAsString());
        JsonNode target = findUserByUsername(beforeDelete, "users_chain_target");
        assertNotNull(target);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/users/{userId}", target.get("id").asLong())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        MvcResult afterDeleteResult = mockMvc.perform(get("/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode afterDelete = objectMapper.readTree(afterDeleteResult.getResponse().getContentAsString());
        assertTrue(findUserByUsername(afterDelete, "users_chain_target") == null);
    }

    private void registerUser(String username, String email, String fullName, String role) throws Exception {
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

    private JsonNode findUserByUsername(JsonNode users, String username) {
        for (JsonNode user : users) {
            if (username.equals(user.get("username").asText())) {
                return user;
            }
        }
        return null;
    }
}



