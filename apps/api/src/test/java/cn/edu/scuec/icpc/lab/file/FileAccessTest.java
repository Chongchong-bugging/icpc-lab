package cn.edu.scuec.icpc.lab.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.edu.scuec.icpc.lab.support.ApiTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

/**
 * WP-P0-05 / TECH-13：上传需权限、元数据按可见性鉴权、下载使用短时签名 URL。
 */
class FileAccessTest extends ApiTestSupport {

    private JsonNode upload(String token, String content, String visibility) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "note.txt", "text/plain",
                content.getBytes(StandardCharsets.UTF_8));
        MvcResult result = mvc.perform(multipart("/api/v1/files")
                        .file(file)
                        .param("visibility", visibility)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    void uploadAndDownloadWithSignedUrl() throws Exception {
        String username = newUsername("file.owner");
        createAccount(username, "附件所有者", "APPLICANT", "SELF");
        String token = loginToken(username, TEST_PASSWORD);

        JsonNode uploaded = upload(token, "hello-icpc", "SCOPED");
        String id = uploaded.get("id").asText();
        String downloadUrl = uploaded.get("downloadUrl").asText();
        assertThat(downloadUrl).startsWith("/api/v1/files/download?token=");

        mvc.perform(get("/api/v1/files/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sha256").isNotEmpty());

        String tokenPart = downloadUrl.substring(downloadUrl.indexOf("token=") + 6);
        byte[] body = mvc.perform(get(downloadUrl))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(body, StandardCharsets.UTF_8)).isEqualTo("hello-icpc");

        String tampered = tokenPart.substring(0, tokenPart.length() - 2) + "xy";
        mvc.perform(get("/api/v1/files/download").param("token", tampered))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_DOWNLOAD_TOKEN"));
    }

    @Test
    void privateFileIsHiddenFromOtherAccounts() throws Exception {
        String owner = newUsername("file.private");
        createAccount(owner, "私有文件所有者", "APPLICANT", "SELF");
        String ownerToken = loginToken(owner, TEST_PASSWORD);
        String privateId = upload(ownerToken, "private", "PRIVATE").get("id").asText();
        String scopedId = upload(ownerToken, "scoped", "SCOPED").get("id").asText();

        String other = newUsername("file.other");
        createAccount(other, "他人", "APPLICANT", "SELF");
        String otherToken = loginToken(other, TEST_PASSWORD);
        mvc.perform(get("/api/v1/files/" + privateId).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
        // SELF 范围不足以读取他人的 SCOPED 附件
        mvc.perform(get("/api/v1/files/" + scopedId).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());

        // PRIVATE 仅本人可见；GLOBAL 也不能越权读取（数据范围默认拒绝）
        mvc.perform(get("/api/v1/files/" + privateId).header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/files/" + scopedId).header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk());
    }

    @Test
    void uploadRequiresAuthenticationAndArchiveCannotBeRead() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "x.txt", "text/plain", "x".getBytes());
        mvc.perform(multipart("/api/v1/files").file(file))
                .andExpect(status().isUnauthorized());

        String owner = newUsername("file.archive");
        createAccount(owner, "归档测试", "APPLICANT", "SELF");
        String token = loginToken(owner, TEST_PASSWORD);
        String id = upload(token, "to-archive", "SCOPED").get("id").asText();

        mvc.perform(delete("/api/v1/files/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/files/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}