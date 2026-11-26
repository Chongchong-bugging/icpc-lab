package cn.edu.scuec.icpc.lab.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B0 集成测试基类：真实 H2(PG 模式) + Flyway + 种子；不 mock 安全链路。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.file.storage-dir=./target/test-files")
public abstract class ApiTestSupport {

    public static final String ADMIN_USERNAME = "admin";
    public static final String ADMIN_PASSWORD = "DevOnly!2026";
    public static final String TEST_PASSWORD = "Passw0rd!2026";
    public static final String REFRESH_COOKIE = "icpc_refresh";

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper objectMapper;

    protected MvcResult loginRaw(String username, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
                .andReturn();
    }

    protected String loginToken(String username, String password) throws Exception {
        MvcResult result = loginRaw(username, password);
        if (result.getResponse().getStatus() != 200) {
            throw new IllegalStateException("登录失败：" + username + " -> " + result.getResponse().getStatus()
                    + " " + result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        }
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("accessToken").asText();
    }

    protected String adminToken() throws Exception {
        return loginToken(ADMIN_USERNAME, ADMIN_PASSWORD);
    }

    protected JsonNode createAccount(String username, String displayName, String roleCode, String scopeType)
            throws Exception {
        String scope = scopeType == null ? "SELF" : scopeType;
        String body = """
                {"name":"%s","username":"%s","password":"%s",
                 "roles":[{"roleCode":"%s","scopeType":"%s"}],
                 "reason":"B0 集成测试"}
                """.formatted(displayName, username, TEST_PASSWORD, roleCode, scope);
        MvcResult result = mvc.perform(post("/api/v1/accounts")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    protected String newUsername(String prefix) {
        return prefix + "." + UUID.randomUUID().toString().substring(0, 8);
    }

    protected static String refreshCookieValue(MvcResult result) {
        String header = result.getResponse().getHeader("Set-Cookie");
        if (header == null) {
            throw new IllegalStateException("响应缺少 Set-Cookie");
        }
        String prefix = REFRESH_COOKIE + "=";
        String first = header.split(";", 2)[0];
        if (!first.startsWith(prefix)) {
            throw new IllegalStateException("Cookie 名不符：" + first);
        }
        return first.substring(prefix.length());
    }

    protected static Cookie refreshCookie(String value) {
        return new Cookie(REFRESH_COOKIE, value);
    }
}