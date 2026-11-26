package cn.edu.scuec.icpc.lab.org;

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

/** WP-07/24 组织与任职：岗位授予联动角色、重复授予拒绝、结束任职即时失权（FR-29 / DOM-19 / NFR-02）。 */
class OrgAssignmentTest extends ApiTestSupport {

    @Test
    void assigningPositionGrantsRoleAndEndingItRevokesAccess() throws Exception {
        String suffix = newUsername("").replace(".", "");
        JsonNode department = objectMapper.readTree(mvc.perform(post("/api/v1/org/departments")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", "SEC" + suffix, "name", "信息安全部 " + suffix, "sortOrder", 50))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SEC" + suffix))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        String departmentId = department.get("id").asText();

        JsonNode term = objectMapper.readTree(mvc.perform(post("/api/v1/org/terms")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", "T" + suffix, "name", "第 " + suffix + " 届"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        String termId = term.get("id").asText();

        mvc.perform(post("/api/v1/org/semesters")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", "S" + suffix, "name", "测试学期 " + suffix,
                                "termId", termId, "sortOrder", 90))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.termId").value(termId));

        // 重复的组织编码被拒绝
        mvc.perform(post("/api/v1/org/departments")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "code", "SEC" + suffix, "name", "重复部门"))))
                .andExpect(status().isConflict());

        String username = newUsername("org.assignee");
        JsonNode account = createAccount(username, "待任岗成员", "APPLICANT", "SELF");
        String personId = account.get("personId").asText();
        String token = loginToken(username, TEST_PASSWORD);

        // 授予岗位 + 联动授予 MENTOR 角色
        JsonNode assignment = objectMapper.readTree(mvc.perform(post("/api/v1/org/position-assignments")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "personId", personId, "positionCode", "MENTOR",
                                "departmentId", departmentId, "termId", termId,
                                "roleCode", "MENTOR", "scopeType", "GLOBAL", "reason", "任命导师"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.positionId").isNotEmpty())
                .andExpect(jsonPath("$.roleAssignmentId").isNotEmpty())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        String assignmentId = assignment.get("id").asText();

        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").value(org.hamcrest.Matchers.hasItem("MENTOR")));

        // 在职期间同岗位不可重复授予
        mvc.perform(post("/api/v1/org/position-assignments")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "personId", personId, "positionCode", "MENTOR",
                                "departmentId", departmentId, "roleCode", "MENTOR", "scopeType", "GLOBAL"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ASSIGNMENT_ACTIVE"));

        // 结束任职：历史保留，角色授权回收，旧令牌立即失效
        mvc.perform(post("/api/v1/org/position-assignments/" + assignmentId + "/end")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"任期届满\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENDED"));
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());

        // 普通申请人无 org:write / position:assign
        String applicantUser = newUsername("org.applicant");
        createAccount(applicantUser, "普通申请人", "APPLICANT", "SELF");
        String applicantToken = loginToken(applicantUser, TEST_PASSWORD);
        mvc.perform(post("/api/v1/org/departments")
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"NOPE\",\"name\":\"越权部门\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/org/position-assignments")
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "personId", personId, "positionCode", "MENTOR"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void teamScopeRequiresScopeId() throws Exception {
        String username = newUsername("org.team.scope");
        JsonNode account = createAccount(username, "TEAM 范围校验", "APPLICANT", "SELF");
        mvc.perform(post("/api/v1/org/position-assignments")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "personId", account.get("personId").asText(), "positionCode", "MENTOR",
                                "roleCode", "MENTOR", "scopeType", "TEAM"))))
                .andExpect(status().isBadRequest());
    }
}