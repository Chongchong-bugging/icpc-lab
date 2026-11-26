-- WP-P0-02 / ADR-0002 / DOM-01, DOM-11, DOM-15, DOM-27
-- 全部 DDL 只使用 PostgreSQL 与 H2(PostgreSQL 模式) 共同支持的能力；
-- PostgreSQL 专属加固（审计 append-only 触发器）见 db/migration/postgresql。
-- 约定：UUID 主键、snake_case、timestamp with time zone、varchar + check（不用数据库枚举）、
--       FK on delete restrict、业务记录不物理删除。

create table department (
    id          uuid                        not null primary key,
    code        varchar(64)                 not null,
    name        varchar(128)                not null,
    parent_id   uuid,
    sort_order  integer                     not null default 0,
    active      boolean                     not null default true,
    created_at  timestamp with time zone    not null default current_timestamp,
    updated_at  timestamp with time zone    not null default current_timestamp,
    constraint uq_department_code unique (code),
    constraint fk_department_parent foreign key (parent_id) references department (id) on delete restrict
);

-- 人员主数据；学号只在学生范围内唯一，教师/管理员允许为空（DOM-01）。
create table person (
    id               uuid                     not null primary key,
    student_no       varchar(32),
    name             varchar(64)              not null,
    gender           varchar(16)              not null default 'UNKNOWN',
    email            varchar(128),
    phone            varchar(32),
    department_id    uuid,
    major            varchar(128),
    grade            integer,
    enrollment_year  integer,
    remark           varchar(500),
    created_at       timestamp with time zone not null default current_timestamp,
    updated_at       timestamp with time zone not null default current_timestamp,
    constraint uq_person_student_no unique (student_no),
    constraint ck_person_gender check (gender in ('MALE', 'FEMALE', 'UNKNOWN')),
    constraint fk_person_department foreign key (department_id) references department (id) on delete restrict
);

-- 账号与人员 1:1（ADR-0002）；token_version 变更即令已签发 token 失效（TECH-12）。
create table user_account (
    id                    uuid                     not null primary key,
    person_id             uuid                     not null,
    username              varchar(64)              not null,
    password_hash         varchar(255)             not null,
    status                varchar(16)              not null default 'ACTIVE',
    failed_attempts       integer                  not null default 0,
    locked_until          timestamp with time zone,
    token_version         bigint                   not null default 1,
    must_change_password  boolean                  not null default false,
    last_login_at         timestamp with time zone,
    created_at            timestamp with time zone not null default current_timestamp,
    updated_at            timestamp with time zone not null default current_timestamp,
    constraint uq_user_account_person unique (person_id),
    constraint uq_user_account_username unique (username),
    constraint ck_user_account_status check (status in ('ACTIVE', 'DISABLED')),
    constraint ck_user_account_failed check (failed_attempts >= 0),
    constraint ck_user_account_token_version check (token_version >= 1),
    constraint fk_user_account_person foreign key (person_id) references person (id) on delete restrict
);

create table role (
    id          uuid                     not null primary key,
    code        varchar(32)              not null,
    name        varchar(64)              not null,
    description varchar(255),
    builtin     boolean                  not null default true,
    active      boolean                  not null default true,
    created_at  timestamp with time zone not null default current_timestamp,
    updated_at  timestamp with time zone not null default current_timestamp,
    constraint uq_role_code unique (code)
);

create table role_permission (
    role_id         uuid        not null,
    permission_code varchar(64) not null,
    constraint pk_role_permission primary key (role_id, permission_code),
    constraint fk_role_permission_role foreign key (role_id) references role (id) on delete restrict
);

-- 角色授予：带数据范围与有效期；失效不物理删除（DOM-11）。
create table role_assignment (
    id          uuid                     not null primary key,
    person_id   uuid                     not null,
    role_id     uuid                     not null,
    scope_type  varchar(16)              not null,
    scope_id    uuid,
    valid_from  timestamp with time zone,
    valid_until timestamp with time zone,
    granted_by  uuid,
    granted_at  timestamp with time zone not null default current_timestamp,
    revoked_at  timestamp with time zone,
    revoked_by  uuid,
    reason      varchar(500),
    created_at  timestamp with time zone not null default current_timestamp,
    updated_at  timestamp with time zone not null default current_timestamp,
    constraint ck_role_assignment_scope check (scope_type in ('SELF', 'TEAM', 'DEPARTMENT', 'GLOBAL')),
    constraint ck_role_assignment_scope_id check (
        (scope_type in ('SELF', 'GLOBAL') and scope_id is null)
        or (scope_type in ('TEAM', 'DEPARTMENT') and scope_id is not null)
    ),
    constraint ck_role_assignment_valid check (valid_until is null or valid_from is null or valid_until > valid_from),
    constraint fk_role_assignment_person foreign key (person_id) references person (id) on delete restrict,
    constraint fk_role_assignment_role foreign key (role_id) references role (id) on delete restrict
);

create table org_position (
    id         uuid                     not null primary key,
    code       varchar(64)              not null,
    name       varchar(64)              not null,
    level      integer                  not null default 0,
    active     boolean                  not null default true,
    created_at timestamp with time zone not null default current_timestamp,
    updated_at timestamp with time zone not null default current_timestamp,
    constraint uq_org_position_code unique (code)
);

create table term (
    id         uuid                     not null primary key,
    code       varchar(32)              not null,
    name       varchar(64)              not null,
    start_date date,
    end_date   date,
    active     boolean                  not null default true,
    created_at timestamp with time zone not null default current_timestamp,
    updated_at timestamp with time zone not null default current_timestamp,
    constraint uq_term_code unique (code),
    constraint ck_term_dates check (end_date is null or start_date is null or end_date >= start_date)
);

create table semester (
    id         uuid                     not null primary key,
    term_id    uuid,
    code       varchar(32)              not null,
    name       varchar(64)              not null,
    start_date date,
    end_date   date,
    active     boolean                  not null default true,
    sort_order integer                  not null default 0,
    created_at timestamp with time zone not null default current_timestamp,
    updated_at timestamp with time zone not null default current_timestamp,
    constraint uq_semester_code unique (code),
    constraint ck_semester_dates check (end_date is null or start_date is null or end_date >= start_date),
    constraint fk_semester_term foreign key (term_id) references term (id) on delete restrict
);

-- 入会申请 7 态（ADR-0002）。open_type 仅在未终结状态等于 type，
-- 以 (applicant_person_id, open_type) 唯一约束模拟 PostgreSQL 部分唯一索引（H2 共同支持）。
create table membership_application (
    id                  uuid                     not null primary key,
    applicant_person_id uuid                     not null,
    type                varchar(16)              not null,
    status              varchar(16)              not null default 'DRAFT',
    current_version     integer                  not null default 1,
    open_type           varchar(16),
    submitted_at        timestamp with time zone,
    decided_at          timestamp with time zone,
    created_by          uuid,
    updated_by          uuid,
    created_at          timestamp with time zone not null default current_timestamp,
    updated_at          timestamp with time zone not null default current_timestamp,
    constraint ck_application_type check (type in ('JOIN', 'TRANSFER', 'EXIT')),
    constraint ck_application_status check (status in ('DRAFT', 'SUBMITTED', 'PROCESSING', 'RETURNED', 'APPROVED', 'REJECTED', 'WITHDRAWN')),
    constraint ck_application_open_type check (open_type is null or open_type = type),
    constraint ck_application_open_state check (
        (open_type is null) = (status in ('APPROVED', 'REJECTED', 'WITHDRAWN'))
    ),
    constraint ck_application_version check (current_version >= 1),
    constraint uq_application_open unique (applicant_person_id, open_type),
    constraint fk_application_person foreign key (applicant_person_id) references person (id) on delete restrict
);

-- 申请内容快照：历史版本不可改写（DOM-15/DATA-05）。
create table application_version (
    id             uuid                     not null primary key,
    application_id uuid                     not null,
    version_no     integer                  not null,
    snapshot       jsonb                    not null,
    change_summary varchar(500),
    created_by     uuid,
    created_at     timestamp with time zone not null default current_timestamp,
    constraint uq_application_version unique (application_id, version_no),
    constraint ck_application_version_no check (version_no >= 1),
    constraint fk_application_version_application foreign key (application_id) references membership_application (id) on delete restrict
);

-- 审核轨迹：谁、何时、何意见，不可覆盖（FR-02）。
create table application_review (
    id                 uuid                     not null primary key,
    application_id     uuid                     not null,
    version_no         integer                  not null,
    reviewer_person_id uuid                     not null,
    decision           varchar(16)              not null,
    comment            varchar(500),
    decided_at         timestamp with time zone not null default current_timestamp,
    created_at         timestamp with time zone not null default current_timestamp,
    constraint ck_application_review_decision check (decision in ('APPROVE', 'REJECT', 'RETURN')),
    constraint uq_application_review unique (application_id, version_no, reviewer_person_id),
    constraint fk_application_review_application foreign key (application_id) references membership_application (id) on delete restrict,
    constraint fk_application_review_reviewer foreign key (reviewer_person_id) references person (id) on delete restrict
);

-- 会员身份：一人最多一条非终态（active_key 由应用维护为 person_id）；
-- 终态不被复活（ADR-0002）。H2 无部分索引，用唯一列 + CHECK 表达。
create table membership (
    id          uuid                     not null primary key,
    person_id   uuid                     not null,
    type        varchar(16)              not null,
    lifecycle   varchar(16)              not null default 'PROBATION',
    assessment  varchar(16)              not null default 'PENDING',
    joined_at   timestamp with time zone,
    exited_at   timestamp with time zone,
    exit_reason varchar(500),
    active_key  uuid,
    created_at  timestamp with time zone not null default current_timestamp,
    updated_at  timestamp with time zone not null default current_timestamp,
    constraint uq_membership_active unique (active_key),
    constraint ck_membership_type check (type in ('TEAM_MEMBER', 'LAB_MEMBER')),
    constraint ck_membership_lifecycle check (lifecycle in ('PROBATION', 'FULL', 'EXITED', 'EXPELLED')),
    constraint ck_membership_assessment check (assessment in ('PENDING', 'QUALIFIED', 'OBSERVING')),
    constraint ck_membership_active_key check (
        (active_key is null) = (lifecycle in ('EXITED', 'EXPELLED'))
    ),
    constraint ck_membership_active_key_person check (active_key is null or active_key = person_id),
    constraint fk_membership_person foreign key (person_id) references person (id) on delete restrict
);

-- 刷新令牌只存哈希；轮换时旧令牌标记 revoked 并记录 replaced_by（TECH-12）。
create table refresh_token (
    id             uuid                     not null primary key,
    account_id     uuid                     not null,
    family_id      uuid                     not null,
    token_hash     varchar(64)              not null,
    token_version  bigint                   not null,
    issued_at      timestamp with time zone not null default current_timestamp,
    expires_at     timestamp with time zone not null,
    revoked_at     timestamp with time zone,
    replaced_by_id uuid,
    ip             varchar(64),
    user_agent     varchar(255),
    constraint uq_refresh_token_hash unique (token_hash),
    constraint fk_refresh_token_account foreign key (account_id) references user_account (id) on delete restrict
);

-- 审计：只增不改（DATA-05 / I-21）。PostgreSQL 触发器见 V2。
create table audit_log (
    id               uuid                     not null primary key,
    occurred_at      timestamp with time zone not null default current_timestamp,
    actor_account_id uuid,
    actor_person_id  uuid,
    actor_name       varchar(64),
    action           varchar(64)              not null,
    object_type      varchar(64),
    object_id        varchar(64),
    before_snapshot  varchar(4000),
    after_snapshot   varchar(4000),
    reason           varchar(500),
    request_id       varchar(64),
    ip               varchar(64),
    user_agent       varchar(255)
);

-- 文件元数据；物理文件存对象存储/本地目录，下载必须经服务端鉴权（TECH-13）。
create table file_object (
    id              uuid                     not null primary key,
    owner_person_id uuid                     not null,
    original_name   varchar(255)             not null,
    stored_name     varchar(128)             not null,
    content_type    varchar(128),
    size_bytes      bigint                   not null,
    sha256          varchar(64)              not null,
    visibility      varchar(16)              not null default 'PRIVATE',
    object_type     varchar(64),
    object_id       uuid,
    created_at      timestamp with time zone not null default current_timestamp,
    updated_at      timestamp with time zone not null default current_timestamp,
    archived_at     timestamp with time zone,
    constraint uq_file_object_stored unique (stored_name),
    constraint ck_file_object_size check (size_bytes >= 0),
    constraint ck_file_object_visibility check (visibility in ('PRIVATE', 'SCOPED', 'INTERNAL')),
    constraint fk_file_object_owner foreign key (owner_person_id) references person (id) on delete restrict
);

-- 种子执行记录：批次、计数、耗时与校验信息（种子规范 §5）。
create table seed_run (
    id             uuid                     not null primary key,
    run_at         timestamp with time zone not null default current_timestamp,
    seed_version   varchar(32)              not null,
    inserted_count integer                  not null default 0,
    updated_count  integer                  not null default 0,
    skipped_count  integer                  not null default 0,
    details        text
);

-- 规则键：未确认阈值 value 为空且 mode=MANUAL，只提示不自动处罚（DOM-12 / I-23）。
create table rule_config (
    id            uuid                     not null primary key,
    rule_key      varchar(128)             not null,
    rule_value    varchar(255),
    mode          varchar(16)              not null,
    description   varchar(255),
    version       integer                  not null default 1,
    source_clause varchar(64),
    updated_at    timestamp with time zone not null default current_timestamp,
    constraint uq_rule_config_key unique (rule_key),
    constraint ck_rule_config_mode check (mode in ('BLOCK', 'WARN', 'MANUAL')),
    constraint ck_rule_config_version check (version >= 1),
    constraint ck_rule_config_manual check (
        (mode = 'MANUAL' and rule_value is null)
        or (mode <> 'MANUAL')
    )
);

-- 身份类型层级（Q-03：TEAM_MEMBER 包含于 LAB_MEMBER）。
create table identity_type (
    id         uuid                     not null primary key,
    code       varchar(64)              not null,
    name       varchar(64)              not null,
    parent_id  uuid,
    sort_order integer                  not null default 0,
    active     boolean                  not null default true,
    created_at timestamp with time zone not null default current_timestamp,
    constraint uq_identity_type_code unique (code),
    constraint fk_identity_type_parent foreign key (parent_id) references identity_type (id) on delete restrict
);

-- 算法大类一级目录（FR-37 的 A 层参考数据；三级扩展在 B2 迁移中增加）。
create table algorithm_category (
    id         uuid                     not null primary key,
    code       varchar(64)              not null,
    name       varchar(64)              not null,
    parent_id  uuid,
    sort_order integer                  not null default 0,
    active     boolean                  not null default true,
    created_at timestamp with time zone not null default current_timestamp,
    updated_at timestamp with time zone not null default current_timestamp,
    constraint uq_algorithm_category_code unique (code),
    constraint fk_algorithm_category_parent foreign key (parent_id) references algorithm_category (id) on delete restrict
);

create index idx_person_department on person (department_id);
create index idx_role_assignment_person on role_assignment (person_id, revoked_at);
create index idx_application_applicant on membership_application (applicant_person_id);
create index idx_application_status on membership_application (status);
create index idx_application_version_application on application_version (application_id);
create index idx_application_review_application on application_review (application_id);
create index idx_membership_person on membership (person_id);
create index idx_refresh_token_account on refresh_token (account_id);
create index idx_refresh_token_family on refresh_token (family_id);
create index idx_audit_log_occurred on audit_log (occurred_at);
create index idx_audit_log_actor on audit_log (actor_person_id);
create index idx_audit_log_object on audit_log (object_type, object_id);
create index idx_file_object_owner on file_object (owner_person_id);