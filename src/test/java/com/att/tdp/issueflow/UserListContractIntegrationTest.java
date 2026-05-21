package com.att.tdp.issueflow;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class UserListContractIntegrationTest extends ContractIntegrationTestSupport {

    @Test
    void getUsersWithValidTokenReturnsExpectedUserFields() throws Exception {
        registerUserAndReturnId("users_contract_dev1", "users.contract.dev1@example.com", "Developer One", "DEVELOPER");
        registerUserAndReturnId("users_contract_dev2", "users.contract.dev2@example.com", "Developer Two", "DEVELOPER");
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

    @ParameterizedTest
    @MethodSource("getUsersUnauthorizedCases")
    void getUsersUnauthorizedCasesReturnExpectedErrorCodes(AuthCase authCase, String expectedErrorCode) throws Exception {
        ResultActions result = switch (authCase) {
            case NO_TOKEN -> mockMvc.perform(get("/users"));
            case INVALID_TOKEN -> mockMvc.perform(get("/users")
                    .header("Authorization", "Bearer invalid.token.value"));
            case EXPIRED_TOKEN -> {
                registerUserAndReturnId("users_contract_expired", "users.contract.expired@example.com", "Expired User", "DEVELOPER");
                String expiredToken = createExpiredToken("users_contract_expired");
                yield mockMvc.perform(get("/users").header("Authorization", "Bearer " + expiredToken));
            }
            case LOGGED_OUT_TOKEN -> {
                registerUserAndReturnId("users_contract_logout", "users.contract.logout@example.com", "Logout User", "DEVELOPER");
                String token = loginAndGetToken("users_contract_logout", "SecurePass1!");
                mockMvc.perform(post("/auth/logout")
                                .header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk());
                yield mockMvc.perform(get("/users").header("Authorization", "Bearer " + token));
            }
        };

        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value(expectedErrorCode))
                .andExpect(jsonPath("$.path").value("/users"))
                .andExpect(jsonPath("$.explanation").isNotEmpty())
                .andExpect(jsonPath("$.action").isNotEmpty());
    }

    @Test
    void userListReflectsCreateAndRemovesDeletedUserInSameFlow() throws Exception {
        registerUserAndReturnId("users_chain_actor", "users.chain.actor@example.com", "Users Chain Actor", "DEVELOPER");
        String token = loginAndGetToken("users_chain_actor", "SecurePass1!");
        registerUserAndReturnId("users_chain_target", "users.chain.target@example.com", "Users Chain Target", "DEVELOPER");

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

    private JsonNode findUserByUsername(JsonNode users, String username) {
        for (JsonNode user : users) {
            if (username.equals(user.get("username").asText())) {
                return user;
            }
        }
        return null;
    }

    private static Stream<Arguments> getUsersUnauthorizedCases() {
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



