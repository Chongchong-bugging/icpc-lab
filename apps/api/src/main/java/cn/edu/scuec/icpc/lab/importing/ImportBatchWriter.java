package cn.edu.scuec.icpc.lab.importing;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 独立事务写批次状态：确认阶段失败时主事务回滚，但 FAILED 状态必须留痕（DOM-24）。
 */
@Component
public class ImportBatchWriter {

    private final JdbcTemplate jdbc;

    public ImportBatchWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID batchId) {
        jdbc.update("update import_batch set status = 'FAILED', updated_at = current_timestamp where id = ?",
                batchId);
    }
}