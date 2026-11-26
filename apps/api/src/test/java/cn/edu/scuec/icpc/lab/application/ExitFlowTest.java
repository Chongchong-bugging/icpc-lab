package cn.edu.scuec.icpc.lab.application;

import cn.edu.scuec.icpc.lab.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** AT-02 退出申请：告知期只提示不阻断，生效后会员身份终止（FR-02 / DOM-07 / DOM-12）。 */
class ExitFlowTest extends ApiTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void exitTakesEffectOnlyAfterNoticePeriod() throws Exception {
        MemberSession member = becomeMember("exit.member", "MEMBER");
        String reviewerUser = newUsername("exit.reviewer");
        createAccount(reviewerUser, "退出审批人", "PRESIDENT", "GLOBAL");
        String reviewerToken = loginToken(reviewerUser, TEST_PASSWORD);

        String applicationId = objectMapper.readTree(mvc.perform(post("/api/v1/applications")
                        .header("Authorization", "Bearer " + member.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "type", "EXIT", "form", "{\"reason\":\"个人原因\"}", "reason", "申请退出"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString()).get("id").asText();
        mvc.perform(post("/api/v1/applications/" + applicationId + "/submit")
                        .header("Authorization", "Bearer " + member.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        mvc.perform(post("/api/v1/applications/" + applicationId + "/review")
                        .header("Authorization", "Bearer " + reviewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\",\"comment\":\"同意退出\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.effectiveAt").isNotEmpty());

        // 告知期未满：只提示，不自动终止（DOM-12 / DENY-04）
        mvc.perform(post("/api/v1/applications/" + applicationId + "/effect")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_YET_EFFECTIVE"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/members/me").header("Authorization", "Bearer " + member.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membership.lifecycle").value("PROBATION"));

        // 模拟告知期已过（测试仅调整生效时间，不改变告知期语义）
        jdbc.update("update membership_application set effective_at = ? where id = ?",
                OffsetDateTime.now().minusDays(1), java.util.UUID.fromString(applicationId));

        mvc.perform(post("/api/v1/applications/" + applicationId + "/effect")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectedAt").isNotEmpty());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/members/me").header("Authorization", "Bearer " + member.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membership.lifecycle").value("EXITED"))
                .andExpect(jsonPath("$.membership.exitedAt").isNotEmpty())
                .andExpect(jsonPath("$.membership.exitReason").isNotEmpty());
    }

    @Test
    void teamMemberMustGiveFifteenWorkdaysNotice() throws Exception {
        MemberSession member = becomeMember("exit.team", "TEAM_MEMBER");
        transferToTeam(member);
        String reviewerUser = newUsername("exit.team.reviewer");
        createAccount(reviewerUser, "集训队退出审批人", "PRESIDENT", "GLOBAL");

        String exitId = createExit(member, "{\"desiredExitDate\":\"" + java.time.LocalDate.now().plusDays(1) + "\"}");
        mvc.perform(post("/api/v1/applications/" + exitId + "/submit")
                        .header("Authorization", "Bearer " + member.token()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXIT_NOTICE_TOO_SHORT"));
    }

    @Test
    void ordinaryMemberExitIsNotBlockedByNoticePeriod() throws Exception {
        MemberSession member = becomeMember("exit.ordinary", "MEMBER");
        String exitId = createExit(member, "{\"desiredExitDate\":\"" + java.time.LocalDate.now().plusDays(1) + "\"}");
        mvc.perform(post("/api/v1/applications/" + exitId + "/submit")
                        .header("Authorization", "Bearer " + member.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
    }

    private String createExit(MemberSession member, String form) throws Exception {
        return objectMapper.readTree(mvc.perform(post("/api/v1/applications")
                        .header("Authorization", "Bearer " + member.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "type", "EXIT", "form", form, "reason", "申请退出"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    private void transferToTeam(MemberSession member) throws Exception {
        String reviewerUser = newUsername("transfer.reviewer");
        createAccount(reviewerUser, "转会审批人", "PRESIDENT", "GLOBAL");
        String reviewerToken = loginToken(reviewerUser, TEST_PASSWORD);
        String transferId = objectMapper.readTree(mvc.perform(post("/api/v1/applications")
                        .header("Authorization", "Bearer " + member.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("type", "TRANSFER", "form", "{}"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("id").asText();
        mvc.perform(post("/api/v1/applications/" + transferId + "/submit")
                        .header("Authorization", "Bearer " + member.token()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/applications/" + transferId + "/review")
                        .header("Authorization", "Bearer " + reviewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/applications/" + transferId + "/effect")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk());
    }

    @Test
    void nonMemberCannotApplyExit() throws Exception {
        String username = newUsername("exit.non-member");
        createAccount(username, "非会员", "APPLICANT", "SELF");
        String token = loginToken(username, TEST_PASSWORD);
        mvc.perform(post("/api/v1/applications")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("type", "EXIT", "form", "{}"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_MEMBER"));
    }
}