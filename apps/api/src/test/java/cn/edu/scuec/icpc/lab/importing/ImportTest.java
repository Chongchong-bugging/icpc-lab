package cn.edu.scuec.icpc.lab.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.edu.scuec.icpc.lab.support.ApiTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

/**
 * WP-P0-13 验收：两阶段导入（预检→确认）、逐行错误、重复提交幂等、文件与类型护栏（DOM-24 / DATA-11 / NFR-05）。
 */
class ImportTest extends ApiTestSupport {

    private static final String PREVIEW = "/api/v1/imports/preview";
    private static final String HEADER = "姓名,学号,邮箱,电话,专业,年级\n";

    @Test
    void previewReportsRowErrorsWithoutWritingBodies_DOM24() throws Exception {
        String csv = HEADER
                + "张三," + studentNo() + ",zhang@example.com,13800000000,计算机,3\n"
                + "," + studentNo() + ",ok@example.com,,,\n";
        MvcResult result = mvc.perform(multipart(PREVIEW)
                        .file(csvFile("members.csv", csv))
                        .param("kind", "MEMBER")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PREVIEW"))
                .andExpect(jsonPath("$.kind").value("MEMBER"))
                .andExpect(jsonPath("$.totalRows").value(2))
                .andExpect(jsonPath("$.validRows").value(1))
                .andExpect(jsonPath("$.invalidRows").value(1))
                .andExpect(jsonPath("$.errorCount").value(1))
                .andExpect(jsonPath("$.duplicate").value(false))
                .andExpect(jsonPath("$.errors[0].rowNo").value(3))
                .andExpect(jsonPath("$.errors[0].code").value("REQUIRED_FIELD"))
                .andExpect(jsonPath("$.errors[0].column").value("姓名"))
                .andReturn();

        String batchId = read(result).get("id").asText();
        mvc.perform(get("/api/v1/imports/" + batchId + "/errors.csv")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(result1 -> assertThat(result1.getResponse().getContentAsString(StandardCharsets.UTF_8))
                        .contains("行号,列,错误码")
                        .contains("REQUIRED_FIELD")
                        .contains("姓名"));
    }

    @Test
    void applyRejectedWhenPreviewHasErrors_DOM24() throws Exception {
        String csv = HEADER + ",NO-STUDENT-NO,x\n";
        String batchId = read(mvc.perform(multipart(PREVIEW)
                        .file(csvFile("bad.csv", csv))
                        .param("kind", "MEMBER")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn()).get("id").asText();

        mvc.perform(multipart("/api/v1/imports/" + batchId + "/apply")
                        .file(csvFile("bad.csv", csv))
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMPORT_HAS_ERRORS"));

        mvc.perform(multipart("/api/v1/imports/" + batchId + "/apply")
                        .file(csvFile("bad.csv", csv))
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IMPORT_BATCH_FAILED"));
    }

    @Test
    void applyIsIdempotentForSameFile_DATA11() throws Exception {
        String csv = HEADER
                + "李四," + studentNo() + ",li@example.com,13900000000,软件工程,2\n"
                + "王五," + studentNo() + ",wang@example.com,13700000000,软件工程,2\n";
        String batchId = read(mvc.perform(multipart(PREVIEW)
                        .file(csvFile("ok.csv", csv))
                        .param("kind", "MEMBER")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.validRows").value(2))
                .andExpect(jsonPath("$.invalidRows").value(0))
                .andExpect(jsonPath("$.duplicate").value(false))
                .andReturn()).get("id").asText();

        mvc.perform(multipart("/api/v1/imports/" + batchId + "/apply")
                        .file(csvFile("ok.csv", csv))
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.appliedAt").exists());

        mvc.perform(multipart("/api/v1/imports/" + batchId + "/apply")
                        .file(csvFile("ok.csv", csv))
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.duplicate").value(true));

        mvc.perform(multipart(PREVIEW)
                        .file(csvFile("ok.csv", csv))
                        .param("kind", "MEMBER")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(batchId))
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.duplicate").value(true));
    }

    @Test
    void guardsFileKindAndHeader_DOM24() throws Exception {
        String token = adminToken();
        String csv = HEADER + "赵六," + studentNo() + ",zhao@example.com,13600000000,网络工程,1\n";

        mvc.perform(multipart(PREVIEW).file(csvFile("ok.csv", csv)).param("kind", "ATTENDANCE")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMPORT_KIND_NOT_SUPPORTED"));

        mvc.perform(multipart(PREVIEW).file(csvFile("ok.csv", csv)).param("kind", "BAD")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMPORT_KIND_INVALID"));

        mvc.perform(multipart(PREVIEW).file(csvFile("ok.csv", "姓名,邮箱\n张三,a@b.com\n")).param("kind", "MEMBER")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMPORT_HEADER_MISSING"));

        String batchId = read(mvc.perform(multipart(PREVIEW).file(csvFile("ok.csv", csv)).param("kind", "MEMBER")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn()).get("id").asText();

        mvc.perform(multipart("/api/v1/imports/" + batchId + "/apply")
                        .file(csvFile("other.csv", HEADER + "钱七," + studentNo() + ",q@example.com,13500000000,网络工程,1\n"))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMPORT_FILE_MISMATCH"));
    }

    @Test
    void quotedCsvCellsParsed_RFC4180() throws Exception {
        String csv = HEADER + "孙八," + studentNo() + ",\"sun@example.com\",\"135 0000 0000\",\"计算机, 软件\",1\n";
        mvc.perform(multipart(PREVIEW).file(csvFile("quoted.csv", csv)).param("kind", "MEMBER")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(1))
                .andExpect(jsonPath("$.invalidRows").value(0));
    }

    @Test
    void importRequiresImportWrite_TECH11() throws Exception {
        String username = newUsername("plain");
        createAccount(username, "普通会员", "MEMBER", "SELF");
        String token = loginToken(username, TEST_PASSWORD);
        mvc.perform(multipart(PREVIEW).file(csvFile("ok.csv", HEADER)).param("kind", "MEMBER")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ 辅助

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private MockMultipartFile csvFile(String name, String csv) {
        return new MockMultipartFile("file", name, "text/csv", csv.getBytes(StandardCharsets.UTF_8));
    }

    private String studentNo() {
        return newUsername("s").replaceAll("[^0-9a-zA-Z]", "");
    }
}