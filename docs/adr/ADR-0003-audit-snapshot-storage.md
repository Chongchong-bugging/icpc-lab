# ADR-0003：审计快照的存储形态（B0）

- **状态**：已采纳（2026-11-26）
- **关联**：ADR-0002（领域模型）、DATA-05 / I-21（审计只增不改）、DOM-27（审计字段）
- **背景**：ADR-0002 将 `audit_log.before_snapshot / after_snapshot` 描述为 `jsonb`。B0 需要在本地无 Docker/PostgreSQL 的环境下用 H2 2.3.232 的 PostgreSQL 模式跑 Flyway 与集成测试；H2 对 `jsonb` 仅作别名接受，JDBC 侧无法像 PostgreSQL 一样以 `org.postgresql.util.PGobject` 写入真实 JSON 文档，Hibernate 的 `SqlTypes.JSON` 映射会把字符串再次序列化（双重编码），审计内容形态不可控。

## 决策

1. `audit_log.before_snapshot / after_snapshot` 在公共迁移中使用 `varchar(4000)` 存 JSON 文本；实体按普通字符串映射。
2. 快照内容由服务端 `AuditService` 统一用 Jackson 序列化，保证是合法 JSON 文本；长度上限 4000 字符，超出由服务端拒绝（B0 快照对象为规则/角色/账号等小对象）。
3. "不可改"不依赖列类型：生产 PostgreSQL 由 `db/migration/postgresql/V2__audit_append_only.sql` 触发器阻断 UPDATE/DELETE/TRUNCATE（DATA-05 / I-21）；应用侧不提供任何审计写改删入口。
4. 若后续需要 JSONB 索引/查询能力，在 B1 迁移中把列 `ALTER ... TYPE jsonb USING ...::jsonb`，同时把实体改为 `JsonNode`/`Map` 映射，二者必须同批完成。

## 后果

- H2 与 PostgreSQL 使用同一份公共迁移，集成测试可直接验证审计写入与查询。
- 失去 JSONB 的路径索引与运算符；B0 的审计查询按 actor/action/object 列过滤，不需要 JSONB 查询。
- 生产仍保留触发器级不可变性，满足审计硬约束。