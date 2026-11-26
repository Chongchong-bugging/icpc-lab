package cn.edu.scuec.icpc.lab.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.edu.scuec.icpc.lab.support.ApiTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * WP-P0-03 验收：登录、刷新轮换、登出、停用即时失效、失败锁定（TECH-12 / NFR-02）。
 */
class AuthFlowTest extends ApiTestSupport {

    @Test
    void loginIssuesAccessTokenAndHttpOnlyRefreshCookie() throws Exception {
        MvcResult result = loginRaw(ADMIN_USERNAME, ADMIN_PASSWORD);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String setCookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(setCookie).contains(REFRESH_COOKIE);
        assertThat(setCookie).containsIgnoringCase("HttpOnly");
        assertThat(setCookie).contains("Path=/api/v1/auth");

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(body.get("accessToken").asText()).isNotBlank();
        assertThat(body.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(body.get("expiresInSeconds").asLong()).isEqualTo(900);
        assertThat(body.get("user").get("roles").toString()).contains("SYSADMIN");
    }

    @Test
    void meRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void meReturnsPermissionCatalog() throws Exception {
        String token = adminToken();
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissionCatalogSize").value(65))
                .andExpect(jsonPath("$.username").value(ADMIN_USERNAME));
    }

    @Test
    void refreshRotatesTokenAndRejectsReuse() throws Exception {
        String username = newUsername("refresh.applicant");
        createAccount(username, "刷新测试", "APPLICANT", "SELF");
        MvcResult login = loginRaw(username, TEST_PASSWORD);
        String originalCookie = refreshCookieValue(login);

        MvcResult first = mvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie(originalCookie)))
                .andExpect(status().isOk())
                .andReturn();
        String rotated = refreshCookieValue(first);
        assertThat(rotated).isNotEqualTo(originalCookie);

        mvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie(originalCookie)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REUSED"));
    }

    @Test
    void logoutRevokesRefreshTokenFamily() throws Exception {
        String username = newUsername("logout.applicant");
        createAccount(username, "登出测试", "APPLICANT", "SELF");
        String cookie = refreshCookieValue(loginRaw(username, TEST_PASSWORD));

        mvc.perform(post("/api/v1/auth/logout").cookie(refreshCookie(cookie)))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie(cookie)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void disabledAccountTokenIsRejectedImmediately() throws Exception {
        String username = newUsername("disabled.applicant");
        JsonNode account = createAccount(username, "停用测试", "APPLICANT", "SELF");
        String accountId = account.get("accountId").asText();
        String token = loginToken(username, TEST_PASSWORD);

        mvc.perform(post("/api/v1/accounts/" + accountId + "/disable")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType("application/json")
                        .content("{\"reason\":\"停用即时失效验收\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, TEST_PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    @Test
    void repeatedFailuresLockAccount() throws Exception {
        String username = newUsername("locked.applicant");
        createAccount(username, "锁定测试", "APPLICANT", "SELF");
        for (int attempt = 0; attempt < 5; attempt++) {
            loginRaw(username, "wrong-password");
        }
        mvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, TEST_PASSWORD)))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }
}