-- WP-P0-11..14 题库与训练计划域增量（FR-06 / FR-07 / FR-09 / FR-37 / FR-38 / FR-40 / BR-05 / BR-21 / NFR-05）。
-- 只做加列/加表；不改写既有数据（DATA-03/11）。回滚：开发期重建 H2；生产需反向迁移。

-- 1) 算法目录三级扩展（DOM-18 / DOM-19 / DOM-21）。
-- name_key = "父级 id（根为 root）|规范化名称"，同级重名由唯一约束兜底（服务层同时做友好 409）。
alter table algorithm_category add column name_key varchar(160);
alter table algorithm_category add column short_name varchar(64);
alter table algorithm_category add column description varchar(1000);
alter table algorithm_category add column stage integer;
alter table algorithm_category add column suggested_difficulty varchar(32);
alter table algorithm_category add column owner_person_id uuid;
alter table algorithm_category add column merged_into_id uuid;
alter table algorithm_category add constraint uq_algorithm_category_name_key unique (name_key);
alter table algorithm_category add constraint ck_algorithm_category_stage check (stage is null or (stage >= 1 and stage <= 3));
alter table algorithm_category add constraint fk_ac_owner foreign key (owner_person_id) references person (id) on delete restrict;
alter table algorithm_category add constraint fk_ac_merged foreign key (merged_into_id) references algorithm_category (id) on delete restrict;
create index idx_ac_parent on algorithm_category (parent_id, sort_order);

-- 2) 分类自定义属性（FR-37）。停用保留历史行（DOM-21）。
create table category_attribute (
    id          uuid                     not null primary key,
    category_id uuid                     not null,
    code        varchar(64)              not null,
    name        varchar(64)              not null,
    value_type  varchar(16)              not null default 'TEXT',
    required    boolean                  not null default false,
    sort_order  integer                  not null default 0,
    active      boolean                  not null default true,
    created_at  timestamp with time zone not null default current_timestamp,
    updated_at  timestamp with time zone not null default current_timestamp,
    constraint uq_category_attribute unique (category_id, code),
    constraint ck_category_attribute_type check (value_type in ('TEXT', 'NUMBER', 'BOOLEAN')),
    constraint fk_ca_category foreign key (category_id) references algorithm_category (id) on delete restrict
);

create index idx_ca_category on category_attribute (category_id, active);

-- 3) 知识点前置关系（FR-39 的图能力；B2 只做存储与无环校验，不自动选路径）。
create table category_relation (
    id             uuid                     not null primary key,
    category_id    uuid                     not null,
    prerequisite_id uuid                    not null,
    note           varchar(500),
    created_at     timestamp with time zone not null default current_timestamp,
    updated_at     timestamp with time zone not null default current_timestamp,
    constraint uq_category_relation unique (category_id, prerequisite_id),
    constraint ck_category_relation_self check (category_id <> prerequisite_id),
    constraint fk_cr_category foreign key (category_id) references algorithm_category (id) on delete restrict,
    constraint fk_cr_prerequisite foreign key (prerequisite_id) references algorithm_category (id) on delete restrict
);

create index idx_cr_prerequisite on category_relation (prerequisite_id);

-- 4) 标签字典（DOM-19）。全局共享，删除以停用代替。
create table tag (
    id         uuid                     not null primary key,
    name       varchar(64)              not null,
    type       varchar(16)              not null default 'TYPE',
    active     boolean                  not null default true,
    created_at timestamp with time zone not null default current_timestamp,
    updated_at timestamp with time zone not null default current_timestamp,
    constraint uq_tag_name_type unique (name, type),
    constraint ck_tag_type check (type in ('TYPE', 'SKILL', 'YEAR', 'LEVEL'))
);

create index idx_tag_type on tag (type, name);

-- 5) 题目（DOM-19 / DOM-22 / DOM-23）。source_key+title_key 唯一阻止静默重复导入。
create table problem (
    id           uuid                     not null primary key,
    code         varchar(64),
    title        varchar(300)             not null,
    source       varchar(64)              not null default 'OTHER',
    source_key   varchar(64)              not null,
    title_key    varchar(320)             not null,
    link         varchar(500),
    difficulty   varchar(32),
    statement    varchar(4000),
    active       boolean                  not null default true,
    created_by   uuid,
    updated_by   uuid,
    created_at   timestamp with time zone not null default current_timestamp,
    updated_at   timestamp with time zone not null default current_timestamp,
    constraint uq_problem_source_title unique (source_key, title_key),
    constraint fk_problem_created_by foreign key (created_by) references person (id) on delete restrict,
    constraint fk_problem_updated_by foreign key (updated_by) references person (id) on delete restrict
);

create index idx_problem_source on problem (source_key, active);
create index idx_problem_code on problem (code);

-- 6) 题目-算法关联（DOM-19：每题恰好一个主算法）。
-- primary_key 仅在主算法行为 problem_id，其余为 null —— 可空唯一列在 H2/PostgreSQL 上允许多行 null。
create table problem_algorithm (
    id          uuid                     not null primary key,
    problem_id  uuid                     not null,
    category_id uuid                     not null,
    is_primary  boolean                  not null default false,
    primary_key uuid,
    active      boolean                  not null default true,
    note        varchar(500),
    created_at  timestamp with time zone not null default current_timestamp,
    constraint uq_problem_category unique (problem_id, category_id),
    constraint uq_problem_algorithm_primary unique (primary_key),
    constraint ck_problem_algorithm_primary check (
        (is_primary = false and primary_key is null) or (is_primary = true and primary_key is not null)
    ),
    constraint fk_pa_problem foreign key (problem_id) references problem (id) on delete restrict,
    constraint fk_pa_category foreign key (category_id) references algorithm_category (id) on delete restrict
);

create index idx_pa_category on problem_algorithm (category_id, active);

-- 7) 题目-标签（多对多，标签只能停用不能物理删除）。
create table problem_tag (
    problem_id uuid                     not null,
    tag_id     uuid                     not null,
    created_at timestamp with time zone not null default current_timestamp,
    primary key (problem_id, tag_id),
    constraint fk_pt_problem foreign key (problem_id) references problem (id) on delete restrict,
    constraint fk_pt_tag foreign key (tag_id) references tag (id) on delete restrict
);

create index idx_pt_tag on problem_tag (tag_id);

-- 8) 导入批次（DOM-24 / DATA-11）。preview 与 apply 两阶段；同 kind+sha256 的已应用批次幂等复用。
-- 不保存原始文件：apply 时要求重新上传同一文件并校验 sha256，错误行由 import_row_error 重生成。
create table import_batch (
    id           uuid                     not null primary key,
    kind         varchar(16)              not null,
    file_name    varchar(300)             not null,
    file_sha256  varchar(64)              not null,
    status       varchar(16)              not null default 'PREVIEW',
    total_rows   integer                  not null default 0,
    valid_rows   integer                  not null default 0,
    invalid_rows integer                  not null default 0,
    created_by   uuid,
    applied_at   timestamp with time zone,
    created_at   timestamp with time zone not null default current_timestamp,
    updated_at   timestamp with time zone not null default current_timestamp,
    constraint ck_import_kind check (kind in ('MEMBER', 'ATTENDANCE', 'RESULT')),
    constraint ck_import_status check (status in ('PREVIEW', 'APPLIED', 'FAILED')),
    constraint ck_import_counts check (total_rows >= 0 and valid_rows >= 0 and invalid_rows >= 0),
    constraint fk_ib_created_by foreign key (created_by) references person (id) on delete restrict
);

create index idx_import_batch_hash on import_batch (kind, file_sha256, status);

-- 9) 导入错误行（只追加；DOM-24 错误行回执）。
create table import_row_error (
    id          uuid                     not null primary key,
    batch_id    uuid                     not null,
    row_no      integer                  not null,
    column_name varchar(64),
    code        varchar(64)              not null,
    message     varchar(500)             not null,
    raw_value   varchar(500),
    created_at  timestamp with time zone not null default current_timestamp,
    constraint fk_ire_batch foreign key (batch_id) references import_batch (id) on delete restrict
);

create index idx_ire_batch on import_row_error (batch_id, row_no);

-- 10) 训练计划（FR-38 / FR-40 / BR-05 / BR-21）。状态机与快照由服务层维护。
create table training_plan (
    id              uuid                     not null primary key,
    term_id         uuid,
    semester_id     uuid,
    kind            varchar(16)              not null default 'SEMESTER',
    title           varchar(200)             not null,
    status          varchar(16)              not null default 'DRAFT',
    current_version integer                  not null default 1,
    content         text,
    published_at    timestamp with time zone,
    published_by    uuid,
    created_by      uuid,
    updated_by      uuid,
    created_at      timestamp with time zone not null default current_timestamp,
    updated_at      timestamp with time zone not null default current_timestamp,
    constraint ck_training_plan_kind check (kind in ('SEMESTER', 'WINTER', 'SUMMER')),
    constraint ck_training_plan_status check (status in ('DRAFT', 'SUBMITTED', 'APPROVED', 'PUBLISHED', 'ARCHIVED')),
    constraint ck_training_plan_version check (current_version >= 1),
    constraint fk_tp_term foreign key (term_id) references term (id) on delete restrict,
    constraint fk_tp_semester foreign key (semester_id) references semester (id) on delete restrict,
    constraint fk_tp_published_by foreign key (published_by) references person (id) on delete restrict,
    constraint fk_tp_created_by foreign key (created_by) references person (id) on delete restrict,
    constraint fk_tp_updated_by foreign key (updated_by) references person (id) on delete restrict
);

create index idx_training_plan_status on training_plan (status, term_id);

-- 11) 训练计划版本快照（DOM-10：已发布内容的任何变更都生成新版本，历史不可改写）。
create table training_plan_version (
    id             uuid                     not null primary key,
    plan_id        uuid                     not null,
    version_no     integer                  not null,
    snapshot       text                     not null,
    change_summary varchar(500),
    created_by     uuid,
    created_at     timestamp with time zone not null default current_timestamp,
    constraint uq_training_plan_version unique (plan_id, version_no),
    constraint ck_training_plan_version_no check (version_no >= 1),
    constraint fk_tpv_plan foreign key (plan_id) references training_plan (id) on delete restrict,
    constraint fk_tpv_created_by foreign key (created_by) references person (id) on delete restrict
);