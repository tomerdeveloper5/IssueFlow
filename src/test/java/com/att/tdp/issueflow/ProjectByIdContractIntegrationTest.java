package com.att.tdp.issueflow;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
class ProjectByIdContractIntegrationTest extends ContractIntegrationTestSupport {

    @Test
    void getProjectByIdWithValidTokenReturnsExpectedFieldsAndValues() throws Exception {
        long ownerId = registerUserAndReturnId(
                "project_by_id_owner_user",
                "project.by.id.owner.user@example.com",
                "Project ById Owner",
                "DEVELOPER"
        );
        String token = loginAndGetToken("project_by_id_owner_user", "SecurePass1!");
        long projectId = createProjectAndReturnId(token, ownerId, "Project ById Happy", "Project ById Description");

        MvcResult result = mockMvc.perform(get("/projects/{projectId}", projectId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode project = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(project.size() == 4);
        assertTrue(project.get("id").asLong() == projectId);
        assertTrue("Project ById Happy".equals(project.get("name").asText()));
        assertTrue("Project ById Description".equals(project.get("description").asText()));
        assertTrue(project.get("ownerId").asLong() == ownerId);
    }

    @ParameterizedTest
    @MethodSource("getProjectByIdUnauthorizedCases")
    void getProjectByIdUnauthorizedCasesReturnExpectedErrorCodes(AuthCase authCase, String expectedErrorCode) throws Exception {
        long targetProjectId = 1L;
        ResultActions result = switch (authCase) {
            case NO_TOKEN -> mockMvc.perform(get("/projects/{projectId}", targetProjectId));
            case INVALID_TOKEN -> mockMvc.perform(get("/projects/{projectId}", targetProjectId)
                    .header("Authorization", "Bearer invalid.token.value"));
            case EXPIRED_TOKEN -> {
                registerUserAndReturnId(
                        "project_by_id_expired_user",
                        "project.by.id.expired.user@example.com",
                        "Project ById Expired",
                        "DEVELOPER"
                );
                String expiredToken = createExpiredToken("project_by_id_expired_user");
                yield mockMvc.perform(get("/projects/{projectId}", targetProjectId)
                        .header("Authorization", "Bearer " + expiredToken));
            }
            case LOGGED_OUT_TOKEN -> {
                registerUserAndReturnId(
                        "project_by_id_logout_user",
                        "project.by.id.logout.user@example.com",
                        "Project ById Logout",
                        "DEVELOPER"
                );
                String token = loginAndGetToken("project_by_id_logout_user", "SecurePass1!");
                mockMvc.perform(post("/auth/logout")
                                .header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk());
                yield mockMvc.perform(get("/projects/{projectId}", targetProjectId)
                        .header("Authorization", "Bearer " + token));
            }
        };

        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value(expectedErrorCode))
                .andExpect(jsonPath("$.path").value("/projects/1"))
                .andExpect(jsonPath("$.explanation").isNotEmpty())
                .andExpect(jsonPath("$.action").isNotEmpty());
    }

    @Test
    void getProjectByIdWithValidTokenAndUnknownProjectReturnsNotFound() throws Exception {
        registerUserAndReturnId(
                "project_by_id_missing_user",
                "project.by.id.missing.user@example.com",
                "Project ById Missing",
                "DEVELOPER"
        );
        String token = loginAndGetToken("project_by_id_missing_user", "SecurePass1!");

        mockMvc.perform(get("/projects/{projectId}", 999999999L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/projects/999999999"))
                .andExpect(jsonPath("$.explanation").isNotEmpty())
                .andExpect(jsonPath("$.action").isNotEmpty());
    }

    @Test
    void getProjectByIdForSoftDeletedProjectReturnsNotFound() throws Exception {
        long ownerId = registerUserAndReturnId(
                "project_by_id_deleted_owner",
                "project.by.id.deleted.owner@example.com",
                "Project ById Deleted",
                "DEVELOPER"
        );
        String token = loginAndGetToken("project_by_id_deleted_owner", "SecurePass1!");
        long projectId = createProjectAndReturnId(token, ownerId, "Project ById Deleted Case", "Deleted case");

        mockMvc.perform(delete("/projects/{projectId}", projectId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/projects/{projectId}", projectId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/projects/" + projectId));
    }

    @Test
    void projectByIdChainIncludesUpdateTicketAndWorkloadProjection() throws Exception {
        long ownerId = registerUserAndReturnId(
                "project_by_id_chain_owner",
                "project.by.id.chain.owner@example.com",
                "Project Chain Owner",
                "DEVELOPER"
        );
        String token = loginAndGetToken("project_by_id_chain_owner", "SecurePass1!");
        long projectId = createProjectAndReturnId(token, ownerId, "Project Chain ById", "Chain by id description");
        long ticketId = createTicketAndReturnId(token, projectId, ownerId, "Chain ticket");

        mockMvc.perform(get("/projects/{projectId}", projectId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(projectId));

        mockMvc.perform(patch("/tickets/{ticketId}", ticketId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "IN_PROGRESS"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/projects/{projectId}/workload", projectId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].openTicketCount").isNumber());
    }

    private static Stream<Arguments> getProjectByIdUnauthorizedCases() {
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



