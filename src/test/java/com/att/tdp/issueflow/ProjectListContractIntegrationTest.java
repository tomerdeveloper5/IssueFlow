package com.att.tdp.issueflow;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
class ProjectListContractIntegrationTest extends ContractIntegrationTestSupport {

    @Test
    void getProjectsWithValidTokenReturnsOnlyActiveProjectFields() throws Exception {
        long ownerId = registerUserAndReturnId(
                "project_list_owner_active",
                "project.list.owner.active@example.com",
                "Project List Owner Active",
                "DEVELOPER"
        );
        String token = loginAndGetToken("project_list_owner_active", "SecurePass1!");
        long activeProjectId = createProjectAndReturnId(token, ownerId, "Projects List Active", "Active description");
        long deletedProjectId = createProjectAndReturnId(token, ownerId, "Projects List Deleted", "Deleted description");

        mockMvc.perform(delete("/projects/{projectId}", deletedProjectId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/projects")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode projects = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode activeProject = findProjectByName(projects, "Projects List Active");
        JsonNode softDeletedProject = findProjectByName(projects, "Projects List Deleted");

        assertNotNull(activeProject);
        assertTrue(activeProject.size() == 4);
        assertTrue(activeProject.get("id").asLong() == activeProjectId);
        assertTrue("Active description".equals(activeProject.get("description").asText()));
        assertTrue(activeProject.get("ownerId").asLong() == ownerId);
        assertNull(softDeletedProject);
    }

    @ParameterizedTest
    @MethodSource("getProjectsUnauthorizedCases")
    void getProjectsUnauthorizedCasesReturnExpectedErrorCodes(AuthCase authCase, String expectedErrorCode) throws Exception {
        ResultActions result = switch (authCase) {
            case NO_TOKEN -> mockMvc.perform(get("/projects"));
            case INVALID_TOKEN -> mockMvc.perform(get("/projects")
                    .header("Authorization", "Bearer invalid.token.value"));
            case EXPIRED_TOKEN -> {
                registerUserAndReturnId(
                        "project_list_expired_user",
                        "project.list.expired.user@example.com",
                        "Project List Expired User",
                        "DEVELOPER"
                );
                String expiredToken = createExpiredToken("project_list_expired_user");
                yield mockMvc.perform(get("/projects").header("Authorization", "Bearer " + expiredToken));
            }
            case LOGGED_OUT_TOKEN -> {
                registerUserAndReturnId(
                        "project_list_logout_user",
                        "project.list.logout.user@example.com",
                        "Project List Logout User",
                        "DEVELOPER"
                );
                String token = loginAndGetToken("project_list_logout_user", "SecurePass1!");
                mockMvc.perform(post("/auth/logout")
                                .header("Authorization", "Bearer " + token))
                        .andExpect(status().isOk());
                yield mockMvc.perform(get("/projects").header("Authorization", "Bearer " + token));
            }
        };

        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value(expectedErrorCode))
                .andExpect(jsonPath("$.path").value("/projects"))
                .andExpect(jsonPath("$.explanation").isNotEmpty())
                .andExpect(jsonPath("$.action").isNotEmpty());
    }

    @Test
    void projectListChainWithDeletedAndRestoreFlow() throws Exception {
        long ownerId = registerUserAndReturnId(
                "project_list_chain_owner",
                "project.list.chain.owner@example.com",
                "Project Chain Owner",
                "DEVELOPER"
        );
        registerUserAndReturnId(
                "project_list_chain_admin",
                "project.list.chain.admin@example.com",
                "Project Chain Admin",
                "ADMIN"
        );
        String ownerToken = loginAndGetToken("project_list_chain_owner", "SecurePass1!");
        String adminToken = loginAndGetToken("project_list_chain_admin", "SecurePass1!");
        long projectId = createProjectAndReturnId(ownerToken, ownerId, "Project Chain", "Project chain description");

        mockMvc.perform(delete("/projects/{projectId}", projectId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/projects/{projectId}", projectId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/projects/{projectId}/restore", projectId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/projects/{projectId}", projectId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Project Chain"));
    }

    private JsonNode findProjectByName(JsonNode projects, String name) {
        for (JsonNode project : projects) {
            if (name.equals(project.get("name").asText())) {
                return project;
            }
        }
        return null;
    }

    private static Stream<Arguments> getProjectsUnauthorizedCases() {
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



