package cn.edu.scuec.icpc.lab.member;

import cn.edu.scuec.icpc.lab.support.ApiTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** WP-10 学期注册与考核：注册幂等、考核只记录不处罚（FR-04 / DOM-12 / DENY-04）。 */
class RegistrationTest extends ApiTestSupport {

    @Test
    void semesterRegistrationAndAssessment() throws Exception {
        MemberSession member = becomeMember("reg.member", "MEMBER");
        String semesterId = firstActiveSemesterId();

        // 首次注册
        String registrationId = objectMapper.readTree(mvc.perform(post("/api/v1/semester-registrations")
                        .header("Authorization", "Bearer " + member.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"semesterId\":\"" + semesterId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REGISTERED"))
                .andExpect(jsonPath("$.assessment").value("PENDING"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).get("id").asText();

        // 重复注册幂等：同一条记录，不重复插入
        mvc.perform(post("/api/v1/semester-registrations")
                        .header("Authorization", "Bearer " + member.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"semesterId\":\"" + semesterId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(registrationId));

        // 管理员列表可按状态/考核结论过滤
        mvc.perform(get("/api/v1/semester-registrations?semesterId=" + semesterId)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + registrationId + "')]").exists());

        // 考核结论只记录，不自动除名
        mvc.perform(post("/api/v1/semester-registrations/" + registrationId + "/assessment")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "assessment", "OBSERVING", "note", "训练时长达标但考核待观察"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assessment").value("OBSERVING"));
        mvc.perform(get("/api/v1/members/me").header("Authorization", "Bearer " + member.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membership.lifecycle").value("PROBATION"))
                .andExpect(jsonPath("$.membership.assessment").value("OBSERVING"));

        mvc.perform(post("/api/v1/semester-registrations/" + registrationId + "/assessment")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assessment\":\"PASSED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ASSESSMENT"));
    }

    @Test
    void nonMemberCannotRegisterSemester() throws Exception {
        String username = newUsername("reg.non-member");
        createAccount(username, "非会员", "APPLICANT", "SELF");
        String token = loginToken(username, TEST_PASSWORD);
        mvc.perform(post("/api/v1/semester-registrations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"semesterId\":\"" + firstActiveSemesterId() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_MEMBER"));
    }

    private String firstActiveSemesterId() throws Exception {
        JsonNode semesters = objectMapper.readTree(mvc.perform(get("/api/v1/org/semesters")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        if (semesters.isEmpty()) {
            throw new IllegalStateException("种子数据缺少学期");
        }
        return semesters.get(0).get("id").asText();
    }
}