package cn.edu.scuec.icpc.lab.trainingplan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.edu.scuec.icpc.lab.support.ApiTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * WP-P0-14 验收：计划状态机、审批复用、发布不可改写、版本快照与提交时限（DOM-10 / FR-38 / FR-40 / BR-05）。
 */
class TrainingPlanTest extends ApiTestSupport {

    private static final String BASE = "/api/v1/training-plans";

    @Test
    void editCreatesVersionSnapshotAndValidatesContent_DOM10() throws Exception {
        String writer = presidentToken();
        JsonNode created = createPlan(writer, "冬季集训计划 " + newUsername("t"), "WINTER", null);
        String id = created.get("id").asText();
        assertThat(created.get("status").asText()).isEqualTo("DRAFT");
        assertThat(created.get("currentVersion").asInt()).isEqualTo(1);
        assertThat(created.get("content").get("goal").asText()).isEqualTo("冲击区域赛金牌");

        mvc.perform(put(BASE + "/" + id)
                        .header("Authorization", "Bearer " + writer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"冬季集训计划（修订）","content":{"goal":"稳住铜牌","weeklyFrequency":4,
                                 "sessionMinutes":150,"items":[{"title":"图论专题","note":"最短路与生成树"}]},
                                 "changeSummary":"调整目标","reason":"教练组讨论"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentVersion").value(2))
                .andExpect(jsonPath("$.content.goal").value("稳住铜牌"));

        mvc.perform(get(BASE + "/" + id + "/versions").header("Authorization", "Bearer " + writer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].versionNo").value(1))
                .andExpect(jsonPath("$[0].content.goal").value("冲击区域赛金牌"))
                .andExpect(jsonPath("$[1].versionNo").value(2))
                .andExpect(jsonPath("$[1].changeSummary").value("调整目标"));

        mvc.perform(put(BASE + "/" + id)
                        .header("Authorization", "Bearer " + writer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"非法次数\",\"content\":{\"weeklyFrequency\":0}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLAN_CONTENT_INVALID"));

        mvc.perform(put(BASE + "/" + id)
                        .header("Authorization", "Bearer " + writer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":{}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLAN_TITLE_REQUIRED"));

        mvc.perform(post(BASE).header("Authorization", "Bearer " + writer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"BAD\",\"title\":\"非法类型\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLAN_KIND_INVALID"));

        mvc.perform(post(BASE).header("Authorization", "Bearer " + writer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"termId\":\"11111111-1111-1111-1111-111111111111\",\"title\":\"届次不存在\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TERM_NOT_FOUND"));
    }

    @Test
    void submitApprovePublishLifecycle_DOM10() throws Exception {
        String writer = presidentToken();
        String approver = presidentToken();
        String id = createPlan(writer, "暑期集训计划 " + newUsername("t"), "SUMMER", null).get("id").asText();

        mvc.perform(post(BASE + "/" + id + "/submit").header("Authorization", "Bearer " + writer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"请审批\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
        assertThat(approvalPendingCount(approver)).isPositive();

        mvc.perform(post(BASE + "/" + id + "/submit").header("Authorization", "Bearer " + writer))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_NOT_DRAFT"));

        mvc.perform(put(BASE + "/" + id).header("Authorization", "Bearer " + writer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"待审期间修改\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_SUBMITTED_IMMUTABLE"));

        mvc.perform(post(BASE + "/" + id + "/publish").header("Authorization", "Bearer " + writer))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_NOT_APPROVED"));

        mvc.perform(post(BASE + "/" + id + "/approve").header("Authorization", "Bearer " + approver)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\",\"comment\":\"同意\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mvc.perform(post(BASE + "/" + id + "/publish").header("Authorization", "Bearer " + approver)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"发布\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.publishedAt").exists())
                .andExpect(jsonPath("$.publishedBy").exists());

        mvc.perform(put(BASE + "/" + id).header("Authorization", "Bearer " + writer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"发布后修改\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_PUBLISHED_IMMUTABLE"));

        mvc.perform(post(BASE + "/" + id + "/approve").header("Authorization", "Bearer " + approver)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_NOT_SUBMITTED"));

        mvc.perform(post(BASE + "/" + id + "/publish").header("Authorization", "Bearer " + approver))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_NOT_APPROVED"));
    }

    @Test
    void rejectReturnsToDraftAndCanResubmit_DOM10() throws Exception {
        String writer = presidentToken();
        String approver = presidentToken();
        String id = createPlan(writer, "退回重提计划 " + newUsername("t"), "WINTER", null).get("id").asText();

        mvc.perform(post(BASE + "/" + id + "/submit").header("Authorization", "Bearer " + writer))
                .andExpect(status().isOk());
        mvc.perform(post(BASE + "/" + id + "/approve").header("Authorization", "Bearer " + approver)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REJECT\",\"comment\":\"训练量不足\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));

        mvc.perform(post(BASE + "/" + id + "/approve").header("Authorization", "Bearer " + approver)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_NOT_SUBMITTED"));

        mvc.perform(post(BASE + "/" + id + "/submit").header("Authorization", "Bearer " + writer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
    }

    @Test
    void selfApprovalDenied_AT04() throws Exception {
        String writer = presidentToken();
        String id = createPlan(writer, "自批计划 " + newUsername("t"), "WINTER", null).get("id").asText();
        mvc.perform(post(BASE + "/" + id + "/submit").header("Authorization", "Bearer " + writer))
                .andExpect(status().isOk());

        mvc.perform(post(BASE + "/" + id + "/approve").header("Authorization", "Bearer " + writer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SELF_APPROVAL_DENIED"));
    }

    @Test
    void reviseAfterPublishCreatesNewVersionAndKeepsHistory_DOM10() throws Exception {
        String writer = presidentToken();
        String approver = presidentToken();
        String id = createPlan(writer, "修订计划 " + newUsername("t"), "WINTER", null).get("id").asText();
        mvc.perform(post(BASE + "/" + id + "/submit").header("Authorization", "Bearer " + writer))
                .andExpect(status().isOk());
        mvc.perform(post(BASE + "/" + id + "/approve").header("Authorization", "Bearer " + approver)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isOk());
        mvc.perform(post(BASE + "/" + id + "/publish").header("Authorization", "Bearer " + approver))
                .andExpect(status().isOk());

        mvc.perform(post(BASE + "/" + id + "/versions").header("Authorization", "Bearer " + writer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"changeSummary\":\"增加图论专题\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.currentVersion").value(2));

        mvc.perform(get(BASE + "/" + id + "/versions").header("Authorization", "Bearer " + writer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].content.goal").value("冲击区域赛金牌"))
                .andExpect(jsonPath("$[1].changeSummary").value("增加图论专题"));

        mvc.perform(post(BASE + "/" + id + "/versions").header("Authorization", "Bearer " + writer))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_NOT_PUBLISHED"));
    }

    @Test
    void semesterSubmitDeadlineComesFromRuleConfig_BR05() throws Exception {
        String admin = adminToken();
        String writer = presidentToken();
        String expiredSemester = createSemester(admin, LocalDate.now().minusDays(100));
        String freshSemester = createSemester(admin, LocalDate.now().minusDays(3));

        String latePlan = createPlan(writer, "过期提交计划 " + newUsername("t"), "SEMESTER", expiredSemester)
                .get("id").asText();
        mvc.perform(post(BASE + "/" + latePlan + "/submit").header("Authorization", "Bearer " + writer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLAN_SUBMIT_DEADLINE_PASSED"));

        String onTimePlan = createPlan(writer, "按期提交计划 " + newUsername("t"), "SEMESTER", freshSemester)
                .get("id").asText();
        mvc.perform(post(BASE + "/" + onTimePlan + "/submit").header("Authorization", "Bearer " + writer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
    }

    @Test
    void planWriteRequiresPlanWrite_TECH11() throws Exception {
        String username = newUsername("plain");
        createAccount(username, "普通会员", "MEMBER", "SELF");
        String token = loginToken(username, TEST_PASSWORD);
        mvc.perform(post(BASE).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"越权计划\"}"))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ 辅助

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String presidentToken() throws Exception {
        String username = newUsername("president");
        createAccount(username, "主席", "PRESIDENT", "GLOBAL");
        return loginToken(username, TEST_PASSWORD);
    }

    private JsonNode createPlan(String token, String title, String kind, String semesterId) throws Exception {
        String body = "{\"kind\":\"" + kind + "\",\"title\":\"" + title + "\""
                + (semesterId == null ? "" : ",\"semesterId\":\"" + semesterId + "\"")
                + ",\"content\":{\"goal\":\"冲击区域赛金牌\",\"weeklyFrequency\":3,\"sessionMinutes\":120,"
                + "\"items\":[{\"title\":\"DP 专题\",\"note\":\"区间DP\"}]},\"changeSummary\":\"初稿\"}";
        return read(mvc.perform(post(BASE).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn());
    }

    private String createSemester(String token, LocalDate startDate) throws Exception {
        String code = newUsername("sem");
        MvcResult result = mvc.perform(post("/api/v1/org/semesters")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"name\":\"测试学期\",\"startDate\":\"" + startDate
                                + "\",\"endDate\":\"" + startDate.plusWeeks(18) + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return read(result).get("id").asText();
    }

    private int approvalPendingCount(String token) throws Exception {
        JsonNode body = read(mvc.perform(get("/api/v1/approvals/todo").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn());
        return body.isArray() ? body.size() : 0;
    }
}