package com.att.tdp.issueflow;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void loginMeLogoutFlowWorks() throws Exception {
        registerUser("admin1", "admin1@example.com", "ADMIN");

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "admin1",
                                "password", "SecurePass1!"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn();

        String token = objectMapper.readTree(loginResult.getResponse().getContentAsString()).get("accessToken").asText();

        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin1"));

        MvcResult meResult = mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        assertTrue(objectMapper.readTree(meResult.getResponse().getContentAsString()).size() == 5);

        mockMvc.perform(post("/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void loginWithValidCredentialsReturnsTokenPayload() throws Exception {
        registerUser("auth_happy_1", "auth_happy_1@example.com", "DEVELOPER");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "auth_happy_1",
                                "password", "SecurePass1!"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").isNumber());

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "auth_happy_1",
                                "password", "SecurePass1!"
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        assertTrue(objectMapper.readTree(loginResult.getResponse().getContentAsString()).size() == 3);
    }

    @Test
    void loginWithWrongPasswordReturnsUnauthorizedError() throws Exception {
        registerUser("auth_wrong_pass_1", "auth_wrong_pass_1@example.com", "DEVELOPER");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "auth_wrong_pass_1",
                                "password", "WrongPass1!"
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_UNAUTHORIZED"))
                .andExpect(jsonPath("$.explanation").isNotEmpty())
                .andExpect(jsonPath("$.action").isNotEmpty());
    }

    @Test
    void loginWithUnknownUsernameReturnsUnauthorizedError() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "unknown_user_auth_case",
                                "password", "SecurePass1!"
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_UNAUTHORIZED"))
                .andExpect(jsonPath("$.explanation").isNotEmpty())
                .andExpect(jsonPath("$.action").isNotEmpty());
    }

    @Test
    void loginWithBlankCredentialsReturnsValidationError() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", " ",
                                "password", " "
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.validationErrors.username").exists())
                .andExpect(jsonPath("$.validationErrors.password").exists());
    }

    @Test
    void loginWithMalformedJsonReturnsBadRequestError() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"broken\",\"password\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Malformed request body"))
                .andExpect(jsonPath("$.explanation").isNotEmpty())
                .andExpect(jsonPath("$.action").isNotEmpty());
    }

    @Test
    void reloginAfterLogoutCreatesNewSessionAndAllowsDifferentSessionMeAccess() throws Exception {
        registerUser("auth_chain_1", "auth_chain_1@example.com", "DEVELOPER");
        registerUser("auth_chain_2", "auth_chain_2@example.com", "DEVELOPER");
        String loginBody = objectMapper.writeValueAsString(Map.of(
                "username", "auth_chain_1",
                "password", "SecurePass1!"
        ));
        MvcResult loginOne = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn();
        String tokenOne = objectMapper.readTree(loginOne.getResponse().getContentAsString()).get("accessToken").asText();

        mockMvc.perform(get("/auth/me").header("Authorization", "Bearer " + tokenOne))
                .andExpect(status().isOk());
        mockMvc.perform(post("/auth/logout").header("Authorization", "Bearer " + tokenOne))
                .andExpect(status().isOk());
        mockMvc.perform(get("/auth/me").header("Authorization", "Bearer " + tokenOne))
                .andExpect(status().isUnauthorized());

        MvcResult loginTwo = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "auth_chain_2",
                                "password", "SecurePass1!"
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        String tokenTwo = objectMapper.readTree(loginTwo.getResponse().getContentAsString()).get("accessToken").asText();
        assertTrue(!tokenTwo.isBlank());
        mockMvc.perform(get("/auth/me").header("Authorization", "Bearer " + tokenTwo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("auth_chain_2"));
    }

    private void registerUser(String username, String email, String role) throws Exception {
        MvcResult registerResult = mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "email", email,
                                "fullName", "Auth Test User",
                                "role", role,
                                "password", "SecurePass1!"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andReturn();
        assertTrue(objectMapper.readTree(registerResult.getResponse().getContentAsString()).size() == 5);
    }
}



