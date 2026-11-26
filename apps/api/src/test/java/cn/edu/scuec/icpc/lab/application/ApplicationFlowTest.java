package cn.edu.scuec.icpc.lab.application;

import cn.edu.scuec.icpc.lab.support.ApiTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** AT-01 入会申请：7 态、退回重提、审批通过 ≠ 生效、禁自批（FR-02 / DOM-07 / D-05 / I-07）。 */
class ApplicationFlowTest extends ApiTestSupport {

    @Test
    void joinApplicationRunsThroughFullLifecycle() throws Exception {
        String applicantUser = newUsername("join.applicant");
        JsonNode applicantAccount = createAccount(applicantUser, "入会申请人", "APPLICANT", "SELF");
        String applicantToken = loginToken(applicantUser, TEST_PASSWORD);
        String reviewerUser = newUsername("review.president");
        createAccount(reviewerUser, "评审会长", "PRESIDENT", "GLOBAL");
        String reviewerToken = loginToken(reviewerUser, TEST_PASSWORD);

        MvcResult created = mvc.perform(post("/api/v1/applications")
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "type", "JOIN", "channel", "COMPETITION",
                                "form", "{\"motivation\":\"想加入实验室\"}", "reason", "首次申请"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.openType").value("JOIN"))
                .andExpect(jsonPath("$.versions.length()").value(1))
                .andReturn();
        String applicationId = json(created).get("id").asText();

        // 他人不能修改本人申请
        String otherUser = newUsername("join.other");
        createAccount(otherUser, "其他申请人", "APPLICANT", "SELF");
        String otherToken = loginToken(otherUser, TEST_PASSWORD);
        mvc.perform(put("/api/v1/applications/" + applicationId)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"form\":\"{}\"}"))
                .andExpect(status().isForbidden());

        // 提交：进入 SUBMITTED 并生成审批实例、给审批人发站内信
        MvcResult submitted = mvc.perform(post("/api/v1/applications/" + applicationId + "/submit")
                        .header("Authorization", "Bearer " + applicantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.approvalInstanceId").isNotEmpty())
                .andReturn();
        String instanceId = json(submitted).get("approvalInstanceId").asText();

        mvc.perform(get("/api/v1/approvals/todo").header("Authorization", "Bearer " + reviewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.bizId=='" + applicationId + "')]").exists());
        mvc.perform(get("/api/v1/notifications?unreadOnly=true").header("Authorization", "Bearer " + reviewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isNotEmpty());

        // 申请人无 application:review，越权审核 403
        mvc.perform(post("/api/v1/applications/" + applicationId + "/review")
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isForbidden());

        // 退回可重提：退回 → 修改生成新版本 → 再次提交
        mvc.perform(post("/api/v1/applications/" + applicationId + "/review")
                        .header("Authorization", "Bearer " + reviewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"RETURN\",\"comment\":\"补充每周训练时间\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETURNED"))
                .andExpect(jsonPath("$.reviews.length()").value(1));
        mvc.perform(put("/api/v1/applications/" + applicationId)
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "form", "{\"motivation\":\"想加入实验室\",\"weekly\":\"12 小时\"}",
                                "changeSummary", "补充训练时间"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentVersion").value(2))
                .andExpect(jsonPath("$.versions.length()").value(2));
        mvc.perform(post("/api/v1/applications/" + applicationId + "/submit")
                        .header("Authorization", "Bearer " + applicantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        // 审批通过 ≠ 生效：APPROVED 但无会员记录
        mvc.perform(post("/api/v1/applications/" + applicationId + "/review")
                        .header("Authorization", "Bearer " + reviewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\",\"comment\":\"同意入会\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.effectedAt").doesNotExist())
                .andExpect(jsonPath("$.effectiveAt").isNotEmpty())
                // 退回后重新提交会开启新的审批实例，旧实例保持历史轨迹
                .andExpect(jsonPath("$.approvalInstanceId").isNotEmpty());
        mvc.perform(get("/api/v1/members/me").header("Authorization", "Bearer " + applicantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membership").doesNotExist());

        // 管理员确认生效，才写入会员记录（PROBATION）
        MvcResult effectResult = mvc.perform(post("/api/v1/applications/" + applicationId + "/effect")
                        .header("Authorization", "Bearer " + adminToken()))
                .andReturn();
        if (effectResult.getResponse().getStatus() != 200) {
            throw new AssertionError("effect 失败：" + effectResult.getResponse().getStatus() + " "
                    + effectResult.getResponse().getContentAsString(StandardCharsets.UTF_8));
        }
        mvc.perform(get("/api/v1/members/me").header("Authorization", "Bearer " + applicantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membership.type").value("LAB_MEMBER"))
                .andExpect(jsonPath("$.membership.lifecycle").value("PROBATION"));

        // 重复入会申请被拒
        mvc.perform(post("/api/v1/applications")
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("type", "JOIN", "form", "{}"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_MEMBER"));
    }

    @Test
    void applicantCannotApproveOwnApplication() throws Exception {
        String presidentUser = newUsername("self.president");
        createAccount(presidentUser, "自批测试会长", "PRESIDENT", "GLOBAL");
        String presidentToken = loginToken(presidentUser, TEST_PASSWORD);

        MvcResult created = mvc.perform(post("/api/v1/applications")
                        .header("Authorization", "Bearer " + presidentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("type", "JOIN", "form", "{}"))))
                .andExpect(status().isOk())
                .andReturn();
        String applicationId = json(created).get("id").asText();
        mvc.perform(post("/api/v1/applications/" + applicationId + "/submit")
                        .header("Authorization", "Bearer " + presidentToken))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/applications/" + applicationId + "/review")
                        .header("Authorization", "Bearer " + presidentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SELF_APPROVAL_DENIED"));

        // 自批失败后状态仍为 SUBMITTED（事务回滚）
        mvc.perform(get("/api/v1/applications/" + applicationId)
                        .header("Authorization", "Bearer " + presidentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}

