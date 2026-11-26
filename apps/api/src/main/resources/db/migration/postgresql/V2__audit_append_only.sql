-- WP-P0-05 / DATA-05 / I-21：审计日志 append-only 的数据库级兜底。
-- 生产库为 PostgreSQL 16；应用账号只应具备 INSERT/SELECT 权限（部署时另行授权）。
create or replace function audit_log_append_only()
    returns trigger
    language plpgsql
as $$
begin
    raise exception 'audit_log is append-only: % not allowed (DATA-05 / I-21)', tg_op
        using errcode = 'restrict_violation';
end;
$$;

create trigger trg_audit_log_no_update_delete
    before update or delete
    on audit_log
    for each row
execute function audit_log_append_only();

create trigger trg_audit_log_no_truncate
    before truncate
    on audit_log
    for each statement
execute function audit_log_append_only();