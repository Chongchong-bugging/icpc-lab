package cn.edu.scuec.icpc.lab.seed;

import static org.assertj.core.api.Assertions.assertThat;

import cn.edu.scuec.icpc.lab.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 种子规范 §2/§4：按稳定 code 幂等，重复执行不产生重复数据、不覆盖管理员修改。
 */
class SeedIdempotencyTest extends ApiTestSupport {

    @Autowired
    private SeedRunner seedRunner;

    @Autowired
    private JdbcTemplate jdbc;

    private int count(String table) {
        Integer value = jdbc.queryForObject("select count(*) from " + table, Integer.class);
        return value == null ? 0 : value;
    }

    @Test
    void rerunDoesNotDuplicateReferenceData() {
        int roles = count("role");
        int permissions = count("role_permission");
        int rules = count("rule_config");
        int departments = count("department");

        assertThat(roles).isEqualTo(11);
        assertThat(rules).isEqualTo(21);
        assertThat(departments).isEqualTo(4);

        seedRunner.run(null);

        assertThat(count("role")).isEqualTo(roles);
        assertThat(count("role_permission")).isEqualTo(permissions);
        assertThat(count("rule_config")).isEqualTo(rules);
        assertThat(count("department")).isEqualTo(departments);
        assertThat(count("user_account")).isGreaterThanOrEqualTo(1);
    }

    @Test
    void manualRulesStayNullAndUnconfirmed() {
        Integer nonNullManual = jdbc.queryForObject("""
                select count(*) from rule_config where mode = 'MANUAL' and rule_value is not null
                """, Integer.class);
        assertThat(nonNullManual).isZero();
        Integer manualCount = jdbc.queryForObject(
                "select count(*) from rule_config where mode = 'MANUAL'", Integer.class);
        assertThat(manualCount).isEqualTo(5);
    }
}