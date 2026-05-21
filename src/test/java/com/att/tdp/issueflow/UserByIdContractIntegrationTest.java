package com.att.tdp.issueflow;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class UserByIdContractIntegrationTest extends ContractIntegrationTestSupport {

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

    @ParameterizedTest
    @MethodSource("getUserByIdUnauthorizedCases")
    void getUserByIdUnauthorizedCasesReturnExpectedErrorCodes(AuthCase authCase, String expectedErrorCode) throws Exception {
        long targetUserId = 1L;
        ResultActions result = switch (authCase) {
            case NO_TOKEN -> mockMvc.perform(get("/users/{userId}", targetUserId));
            case INVALID_TOKEN -> mockMvc.perform(get("/users/{userId}", targetUserId)
                    .header("Authorization", "Bearer invalid.token.value"));
            case EXPIRED_TOKEN -> {
                registerUserAndReturnId(
                        "user_by_id_expired",
                        "user.by.id.expired@example.com",
                        "Expired User",
                        "DEVELOPER"
                );
                String expiredToken = createExpiredToken("user_by_id_expired");
                yield mockMvc.perform(get("/users/{userId}", targetUserId)
                        .header("Authorization", "Bearer " + expiredToken));
            }
            case LOGGED_OUT_TOKEN -> {
                registerUserAndReturnId(
                        "user_by_id_logout",
                        "user.by.id.logout@example.com",
                        "Logout User",
                        "DEVELOPER"
                );
                String token = loginAndGetToken("user_by_id_logout", "SecurePass1!");
                mockMvc.perform(post("/auth/logout")
                                .header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk());
                yield mockMvc.perform(get("/users/{userId}", targetUserId)
                        .header("Authorization", "Bearer " + token));
            }
        };

        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value(expectedErrorCode))
                .andExpect(jsonPath("$.path").value("/users/1"))
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

    @Test
    void userByIdReflectsUpdateThenReturnsNotFoundAfterDelete() throws Exception {
        long actorId = registerUserAndReturnId(
                "user_by_id_chain_actor",
                "user.by.id.chain.actor@example.com",
                "Chain Actor",
                "DEVELOPER"
        );
        long userId = registerUserAndReturnId(
                "user_by_id_chain",
                "user.by.id.chain@example.com",
                "Chain User",
                "DEVELOPER"
        );
        String token = loginAndGetToken("user_by_id_chain_actor", "SecurePass1!");

        mockMvc.perform(post("/users/update/{userId}", userId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Chain User Updated",
                                "role", "ADMIN"
                        ))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/users/{userId}", userId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Chain User Updated"))
                .andExpect(jsonPath("$.role").value("ADMIN"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/users/{userId}", userId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/users/{userId}", userId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        assertTrue(actorId > 0);
    }

    private static Stream<Arguments> getUserByIdUnauthorizedCases() {
        return Stream.of(
                Arguments.of(AuthCase.NO_TOKEN, "AUTH_REQUIRED"),
                Arguments.of(AuthCase.INVALID_TOKEN, "AUTH_INVALID_TOKEN"),
                Arguments.of(AuthCase.EXPIRED_TOKEN, "AUTH_TOKEN_EXPIRED"),
                Arguments.of(AuthCase.LOGGED_OUT_TOKEN, "AUTH_LOGGED_OUT_TOKEN")
        );
    }

    private enum AuthCase {
        NO_TOKEN,
        INVALID_TOKEN,
        EXPIRED_TOKEN,
        LOGGED_OUT_TOKEN
    }

}



