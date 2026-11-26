-- H2 仅用于本地开发与测试，不支持 PL/pgSQL 触发器。
-- append-only 由应用层保证：审计只有插入入口（AuditService），
-- 不存在更新/删除 API；生产 PostgreSQL 由 V2__audit_append_only.sql 触发器兜底。
select 1;