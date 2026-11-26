package cn.edu.scuec.icpc.lab.audit;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * 仅暴露追加与查询能力；不提供 delete/deleteAll 的调用入口（DATA-05 / I-21）。
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {
}