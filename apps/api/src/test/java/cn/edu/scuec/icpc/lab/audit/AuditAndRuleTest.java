package cn.edu.scuec.icpc.lab.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.edu.scuec.icpc.lab.support.ApiTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * WP-P0-05 验收：审计记录主体/对象/前后快照/请求号，且不存在修改入口（DATA-05 / I-21 / DOM-27）。
 */
class AuditAndRuleTest extends ApiTestSupport {

    @Test
    void ruleUpdateWritesAuditWithBeforeAndAfter() throws Exception {
        String token = adminToken();
        mvc.perform(put("/api/v1/rules/group.popular.max")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"value\":\"6\",\"mode\":\"WARN\",\"sourceClause\":\"DOM-12\",\"reason\":\"审计验收\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("group.popular.max"))
                .andExpect(jsonPath("$.value").value("6"))
                .andExpect(jsonPath("$.version").value(2));

        MvcResult result = mvc.perform(get("/api/v1/audit-logs")
                        .param("action", "rule.update")
                        .param("objectId", "group.popular.max")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(body.get("total").asLong()).isGreaterThanOrEqualTo(1);
        JsonNode item = body.get("items").get(0);
        assertThat(item.get("actorName").asText()).isEqualTo("系统管理员");
        assertThat(item.get("beforeSnapshot").asText()).contains("group.popular.max");
        assertThat(item.get("afterSnapshot").asText()).contains("\"6\"");
        assertThat(item.get("requestId").asText()).isNotBlank();
    }

    @Test
    void manualRuleRejectsNumericValue() throws Exception {
        mvc.perform(put("/api/v1/rules/absence.consecutive_limit")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType("application/json")
                        .content("{\"value\":\"3\",\"mode\":\"MANUAL\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MANUAL_RULE_MUST_BE_NULL"));
    }

    @Test
    void auditLogsHaveNoMutationEndpoint() throws Exception {
        // 路由不存在（而不是被允许的 DELETE），这是"无修改入口"的第一道保证；生产库另有触发器
        mvc.perform(delete("/api/v1/audit-logs/00000000-0000-0000-0000-000000000000")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void ruleUpdateRequiresReasonFieldPresentInAudit() throws Exception {
        // 未确认阈值保持 MANUAL + 空值：种子必须为 5 个 MANUAL 键
        MvcResult result = mvc.perform(get("/api/v1/rules")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode rules = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        long manual = 0;
        for (JsonNode rule : rules) {
            if ("MANUAL".equals(rule.get("mode").asText())) {
                manual++;
                // Jackson non_null 会省略空值字段，视为 null
                assertThat(rule.hasNonNull("value")).isFalse();
            }
        }
        assertThat(manual).isGreaterThanOrEqualTo(5);
    }
}