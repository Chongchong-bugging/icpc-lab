package cn.edu.scuec.icpc.lab.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.edu.scuec.icpc.lab.support.ApiTestSupport;
import org.junit.jupiter.api.Test;

/**
 * WP-P0-04 / AT-11：权限点负向（403）与正向（200），服务端拒绝而非前端隐藏。
 */
class AuthorizationTest extends ApiTestSupport {

    @Test
    void applicantCannotReadAccountsOrAudit() throws Exception {
        String username = newUsername("authz.applicant");
        createAccount(username, "越权测试", "APPLICANT", "SELF");
        String token = loginToken(username, TEST_PASSWORD);

        mvc.perform(get("/api/v1/accounts").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(get("/api/v1/audit-logs").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/roles").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void applicantCanReadRulesButCannotWrite() throws Exception {
        String username = newUsername("authz.rules");
        createAccount(username, "规则测试", "APPLICANT", "SELF");
        String token = loginToken(username, TEST_PASSWORD);

        mvc.perform(get("/api/v1/rules").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/rules/mentor.max_trainees")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"value\":\"5\",\"mode\":\"BLOCK\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/v1/rules"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sysadminCanReadAdminResources() throws Exception {
        String token = adminToken();
        mvc.perform(get("/api/v1/accounts").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
        mvc.perform(get("/api/v1/roles").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/audit-logs").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}