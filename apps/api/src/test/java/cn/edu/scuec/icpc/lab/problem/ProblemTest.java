package cn.edu.scuec.icpc.lab.problem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.edu.scuec.icpc.lab.support.ApiTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * WP-P0-12 验收：一主算法、来源+标题查重、标签校验、组合筛选与分页（DOM-19 / DOM-23 / DATA-09 / ENG-03）。
 */
class ProblemTest extends ApiTestSupport {

    private static final String BASE = "/api/v1/problems";

    @Test
    void createRequiresExactlyOnePrimaryAlgorithm_DOM19() throws Exception {
        String token = adminToken();
        List<String> categories = rootCategories(token);

        mvc.perform(post(BASE)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(problemBody("双主算法题", "CODEFORCES",
                                List.<String[]>of(new String[]{categories.get(0), "true"},
                                        new String[]{categories.get(1), "true"}), null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MULTIPLE_PRIMARY_ALGORITHMS"));

        mvc.perform(post(BASE)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(problemBody("无主算法题", "CODEFORCES",
                                List.<String[]>of(new String[]{categories.get(0), "false"}), null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRIMARY_ALGORITHM_REQUIRED"));

        mvc.perform(post(BASE)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"空算法题\",\"source\":\"CODEFORCES\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRIMARY_ALGORITHM_REQUIRED"));

        JsonNode tag = createTag(token, newUsername("标签"), "SKILL");
        MvcResult created = mvc.perform(post(BASE)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(problemBody("正常题 " + newUsername("t"), "CODEFORCES",
                                List.<String[]>of(new String[]{categories.get(0), "true"}), tag.get("id").asText())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.algorithms.length()").value(1))
                .andExpect(jsonPath("$.algorithms[0].primary").value(true))
                .andExpect(jsonPath("$.tags.length()").value(1))
                .andReturn();
        assertThat(read(created).get("id").asText()).isNotBlank();
    }

    @Test
    void duplicateSourceAndTitleRejected_DOM23() throws Exception {
        String token = adminToken();
        List<String> categories = rootCategories(token);
        String title = "重复题 " + newUsername("dup");
        String body = problemBody(title, "CODEFORCES", List.<String[]>of(new String[]{categories.get(0), "true"}), null);

        mvc.perform(post(BASE).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        mvc.perform(post(BASE).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROBLEM_DUPLICATE"));

        mvc.perform(post(BASE).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(problemBody(title, "ATCODER", List.<String[]>of(new String[]{categories.get(0), "true"}), null)))
                .andExpect(status().isOk());
    }

    @Test
    void updateMovesPrimaryAlgorithmAndKeepsSinglePrimary_DOM19() throws Exception {
        String token = adminToken();
        List<String> categories = rootCategories(token);
        JsonNode created = createProblem(token, "改主算法题 " + newUsername("p"), "CODEFORCES",
                List.<String[]>of(new String[]{categories.get(0), "true"}), null);
        String id = created.get("id").asText();

        mvc.perform(put(BASE + "/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"algorithms\":[{\"categoryId\":\"" + categories.get(0) + "\",\"primary\":false},"
                                + "{\"categoryId\":\"" + categories.get(1) + "\",\"primary\":true}],"
                                + "\"reason\":\"纠正主算法\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.algorithms.length()").value(2))
                .andExpect(jsonPath("$.algorithms[0].primary").value(true))
                .andExpect(jsonPath("$.algorithms[0].categoryId").value(categories.get(1)))
                .andExpect(jsonPath("$.algorithms[1].primary").value(false));

        mvc.perform(put(BASE + "/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"algorithms\":[{\"categoryId\":\"" + categories.get(0) + "\",\"primary\":false},"
                                + "{\"categoryId\":\"" + categories.get(1) + "\",\"primary\":false}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRIMARY_ALGORITHM_REQUIRED"));
    }

    @Test
    void filtersByCategoryTagAndKeywordWithCappedPageSize_DATA09() throws Exception {
        String token = adminToken();
        List<String> categories = rootCategories(token);
        JsonNode tag = createTag(token, newUsername("筛选标签"), "TYPE");
        String keyword = "KW" + newUsername("k").replaceAll("[^0-9a-zA-Z]", "");
        String tagId = tag.get("id").asText();

        createProblem(token, keyword + " 一", "CODEFORCES", List.<String[]>of(new String[]{categories.get(0), "true"}), tagId);
        createProblem(token, keyword + " 二", "CODEFORCES", List.<String[]>of(new String[]{categories.get(0), "true"}), null);
        createProblem(token, keyword + " 三", "ATCODER", List.<String[]>of(new String[]{categories.get(1), "true"}), null);

        mvc.perform(get(BASE).param("keyword", keyword).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));

        mvc.perform(get(BASE).param("keyword", keyword).param("categoryId", categories.get(0))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2));

        mvc.perform(get(BASE).param("keyword", keyword).param("tagId", tagId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));

        mvc.perform(get(BASE).param("keyword", keyword).param("source", "atcoder")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));

        mvc.perform(get(BASE).param("keyword", keyword).param("size", "500")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void deactivateExcludesProblemFromActiveList_DOM11() throws Exception {
        String token = adminToken();
        List<String> categories = rootCategories(token);
        String keyword = "DE" + newUsername("d").replaceAll("[^0-9a-zA-Z]", "");
        JsonNode created = createProblem(token, keyword, "CODEFORCES",
                List.<String[]>of(new String[]{categories.get(0), "true"}), null);
        String id = created.get("id").asText();

        mvc.perform(post(BASE + "/" + id + "/deactivate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"题面失效\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mvc.perform(get(BASE).param("keyword", keyword).param("active", "true")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));

        mvc.perform(get(BASE).param("keyword", keyword).param("active", "false")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void tagDictionaryValidatesTypeAndInactiveReuse_DOM19() throws Exception {
        String token = adminToken();
        String name = newUsername("标签");
        JsonNode tag = createTag(token, name, "LEVEL");

        mvc.perform(post("/api/v1/tags").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"type\":\"LEVEL\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TAG_EXISTS"));

        mvc.perform(post("/api/v1/tags").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"类型非法" + name + "\",\"type\":\"BAD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TAG_TYPE_INVALID"));

        mvc.perform(post("/api/v1/tags/" + tag.get("id").asText() + "/deactivate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        List<String> categories = rootCategories(token);
        mvc.perform(post(BASE).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(problemBody("停用标签题 " + newUsername("t"), "CODEFORCES",
                                List.<String[]>of(new String[]{categories.get(0), "true"}), tag.get("id").asText())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TAG_INACTIVE"));
    }

    @Test
    void problemWriteRequiresProblemWrite_TECH11() throws Exception {
        String username = newUsername("plain");
        createAccount(username, "普通会员", "MEMBER", "SELF");
        String token = loginToken(username, TEST_PASSWORD);
        List<String> categories = rootCategories(adminToken());

        mvc.perform(post(BASE).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(problemBody("越权题 " + newUsername("x"), "CODEFORCES",
                                List.<String[]>of(new String[]{categories.get(0), "true"}), null)))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ 辅助

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private List<String> rootCategories(String token) throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/algorithm-categories").param("includeInactive", "false")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = read(result);
        assertThat(body.size()).isGreaterThanOrEqualTo(2);
        return List.of(body.get(0).get("id").asText(), body.get(1).get("id").asText());
    }

    private JsonNode createTag(String token, String name, String type) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/tags")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"type\":\"" + type + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return read(result);
    }

    private JsonNode createProblem(String token, String title, String source, List<String[]> algorithms,
                                   String tagId) throws Exception {
        MvcResult result = mvc.perform(post(BASE)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(problemBody(title, source, algorithms, tagId)))
                .andExpect(status().isOk())
                .andReturn();
        return read(result);
    }

    private String problemBody(String title, String source, List<String[]> algorithms, String tagId) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("title", title);
        body.put("source", source);
        ArrayNode nodes = body.putArray("algorithms");
        for (String[] algorithm : algorithms) {
            nodes.addObject().put("categoryId", algorithm[0]).put("primary", Boolean.parseBoolean(algorithm[1]));
        }
        if (tagId != null) {
            body.putArray("tagIds").add(tagId);
        }
        return body.toString();
    }
}