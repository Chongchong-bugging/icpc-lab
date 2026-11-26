package cn.edu.scuec.icpc.lab.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.edu.scuec.icpc.lab.support.ApiTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * WP-P0-11 验收：算法目录三级、同级唯一、前后置无环、停用/合并不破坏历史（DOM-18 / DOM-19 / DOM-20 / DOM-21）。
 */
class CategoryTest extends ApiTestSupport {

    private static final String BASE = "/api/v1/algorithm-categories";

    @Test
    void categoryTreeAllowsThreeLevelsAndRejectsFourth_DOM18() throws Exception {
        String token = adminToken();
        JsonNode root = createCategory(token, code(), "根分类", null);
        JsonNode child = createCategory(token, code(), "二级分类", root.get("id").asText());
        JsonNode grandchild = createCategory(token, code(), "三级分类", child.get("id").asText());

        mvc.perform(post(BASE)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code() + "\",\"name\":\"四级分类\",\"parentId\":\""
                                + grandchild.get("id").asText() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CATEGORY_DEPTH_EXCEEDED"));

        mvc.perform(get(BASE + "/" + grandchild.get("id").asText())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value(3));
    }

    @Test
    void sameSiblingNameRejectedButDifferentParentsAllowed_DOM18() throws Exception {
        String token = adminToken();
        String sharedName = "同名分类";
        createCategory(token, code(), sharedName, null);

        mvc.perform(post(BASE)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code() + "\",\"name\":\"" + sharedName + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_NAME_EXISTS"));

        JsonNode parentA = createCategory(token, code(), "父A", null);
        JsonNode parentB = createCategory(token, code(), "父B", null);
        createCategory(token, code(), sharedName, parentA.get("id").asText());
        createCategory(token, code(), sharedName, parentB.get("id").asText());
    }

    @Test
    void relationGraphRejectsSelfAndCycle_DOM20() throws Exception {
        String token = adminToken();
        JsonNode first = createCategory(token, code(), "图论A", null);
        JsonNode second = createCategory(token, code(), "图论B", null);
        String firstId = first.get("id").asText();
        String secondId = second.get("id").asText();

        mvc.perform(post(BASE + "/" + firstId + "/relations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prerequisiteId\":\"" + firstId + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CATEGORY_RELATION_SELF"));

        mvc.perform(post(BASE + "/" + firstId + "/relations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prerequisiteId\":\"" + secondId + "\",\"note\":\"B 先修\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mvc.perform(post(BASE + "/" + secondId + "/relations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prerequisiteId\":\"" + firstId + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_RELATION_CYCLE"));

        mvc.perform(delete(BASE + "/" + firstId + "/relations/" + secondId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void deactivateKeepsRowAndBlocksNewChildren_DOM21() throws Exception {
        String token = adminToken();
        JsonNode parent = createCategory(token, code(), "可停用父", null);
        String parentId = parent.get("id").asText();
        createCategory(token, code(), "已有子分类", parentId);

        mvc.perform(post(BASE + "/" + parentId + "/deactivate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"课程调整\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_HAS_CHILDREN"));

        JsonNode leaf = createCategory(token, code(), "叶子分类", null);
        mvc.perform(post(BASE + "/" + leaf.get("id").asText() + "/deactivate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"课程调整\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mvc.perform(post(BASE)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code() + "\",\"name\":\"新子分类\",\"parentId\":\""
                                + leaf.get("id").asText() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_PARENT_INACTIVE"));
    }

    @Test
    void mergeMovesProblemsAndKeepsAlias_DOM21() throws Exception {
        String token = adminToken();
        JsonNode source = createCategory(token, code(), "源分类", null);
        JsonNode target = createCategory(token, code(), "目标分类", null);
        String sourceId = source.get("id").asText();
        String targetId = target.get("id").asText();

        JsonNode problem = createProblem(token, "合并检查题 " + newUsername("p"), sourceId);
        String problemId = problem.get("id").asText();

        mvc.perform(post(BASE + "/" + sourceId + "/merge")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetId\":\"" + targetId + "\",\"reason\":\"合并同类\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(targetId));

        mvc.perform(get(BASE + "/" + sourceId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.mergedIntoId").value(targetId));

        mvc.perform(get("/api/v1/problems/" + problemId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.algorithms.length()").value(1))
                .andExpect(jsonPath("$.algorithms[0].categoryId").value(targetId));

        mvc.perform(get("/api/v1/problems").param("categoryId", targetId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(problemId));

        mvc.perform(post(BASE + "/" + targetId + "/merge")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetId\":\"" + targetId + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CATEGORY_MERGE_SELF"));
    }

    @Test
    void mergeRejectsNonSiblingTarget_DOM21() throws Exception {
        String token = adminToken();
        JsonNode root = createCategory(token, code(), "合并父", null);
        JsonNode child = createCategory(token, code(), "合并子", root.get("id").asText());
        JsonNode other = createCategory(token, code(), "另一根", null);

        mvc.perform(post(BASE + "/" + child.get("id").asText() + "/merge")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetId\":\"" + other.get("id").asText() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CATEGORY_MERGE_NOT_SIBLING"));
    }

    @Test
    void attributeUpsertValidatesValueType_DOM18() throws Exception {
        String token = adminToken();
        JsonNode category = createCategory(token, code(), "属性分类", null);
        String id = category.get("id").asText();

        mvc.perform(post(BASE + "/" + id + "/attributes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"weight\",\"name\":\"权重\",\"valueType\":\"NUMBER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valueType").value("NUMBER"));

        mvc.perform(post(BASE + "/" + id + "/attributes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"weight\",\"name\":\"权重（更新）\",\"valueType\":\"NUMBER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("权重（更新）"));

        mvc.perform(get(BASE + "/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attributes.length()").value(1))
                .andExpect(jsonPath("$.attributes[0].code").value("WEIGHT"));

        mvc.perform(post(BASE + "/" + id + "/attributes")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"bad\",\"name\":\"非法类型\",\"valueType\":\"JSON\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CATEGORY_ATTRIBUTE_TYPE_INVALID"));
    }

    @Test
    void categoryWriteRequiresCategoryWrite_TECH11() throws Exception {
        String username = newUsername("plain");
        createAccount(username, "普通会员", "MEMBER", "SELF");
        String token = loginToken(username, TEST_PASSWORD);

        mvc.perform(post(BASE)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code() + "\",\"name\":\"越权分类\"}"))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ 辅助

    private String code() {
        return "C" + newUsername("x").replaceAll("[^A-Za-z0-9]", "").substring(1);
    }

    private JsonNode createCategory(String token, String code, String name, String parentId) throws Exception {
        ObjectNode body = objectMapper.createObjectNode().put("code", code).put("name", name);
        if (parentId != null) {
            body.put("parentId", parentId);
        }
        MvcResult result = mvc.perform(post(BASE)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.toString()))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private JsonNode createProblem(String token, String title, String categoryId) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("title", title);
        body.put("source", "CUSTOM");
        ObjectNode algorithm = objectMapper.createObjectNode().put("categoryId", categoryId).put("primary", true);
        body.putArray("algorithms").add(algorithm);
        MvcResult result = mvc.perform(post("/api/v1/problems")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.toString()))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}