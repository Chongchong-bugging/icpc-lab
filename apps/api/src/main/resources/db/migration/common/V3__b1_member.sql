-- B1 会员域增量（FR-01..05 / FR-29）。H2 PostgreSQL 模式与 PostgreSQL 16 共同语法。
-- 只做加列/加表；不改写既有数据（DATA-03/11）。回滚：见 docs/plan/B1-plan.md（开发期重建 H2；生产需反向迁移）。

-- 1) 申请双路径（FR-02）：竞赛类 / 综合管理类。
alter table membership_application add column channel varchar(16) not null default 'GENERAL';
alter table membership_application add constraint ck_application_channel check (channel in ('COMPETITION', 'GENERAL'));
alter table membership_application add column form jsonb;
alter table membership_application add column effective_at timestamp with time zone;
alter table membership_application add column effected_at timestamp with time zone;
-- 审批通过 != 生效（DOM-08）：effective_at 仅由显式生效动作写入。

-- 2) 组织任职与任期（FR-01）。active_key 非空=当前有效任职，结束时置空，保留历史行。
create table position_assignment (
    id                  uuid                     not null primary key,
    person_id           uuid                     not null,
    position_id         uuid                     not null,
    department_id       uuid,
    term_id             uuid,
    role_assignment_id  uuid,
    start_date          date                     not null,
    end_date            date,
    status              varchar(16)              not null default 'ACTIVE',
    active_key          varchar(128),
    reason              varchar(500),
    granted_by          uuid,
    ended_by            uuid,
    ended_at            timestamp with time zone,
    created_at          timestamp with time zone not null default current_timestamp,
    updated_at          timestamp with time zone not null default current_timestamp,
    constraint uq_position_assignment_active unique (active_key),
    constraint ck_position_assignment_status check (status in ('ACTIVE', 'ENDED')),
    constraint ck_position_assignment_dates check (end_date is null or end_date >= start_date),
    constraint fk_pa_person foreign key (person_id) references person (id) on delete restrict,
    constraint fk_pa_position foreign key (position_id) references org_position (id) on delete restrict,
    constraint fk_pa_department foreign key (department_id) references department (id) on delete restrict,
    constraint fk_pa_term foreign key (term_id) references term (id) on delete restrict,
    constraint fk_pa_role_assignment foreign key (role_assignment_id) references role_assignment (id) on delete restrict
);

create index idx_pa_person on position_assignment (person_id);
create index idx_pa_position on position_assignment (position_id);

-- 3) 身份变更留史（FR-03）：只追加。
create table identity_history (
    id          uuid                     not null primary key,
    person_id   uuid                     not null,
    dimension   varchar(16)              not null,
    from_value  varchar(64),
    to_value    varchar(64)              not null,
    reason      varchar(500),
    changed_by  uuid,
    changed_at  timestamp with time zone not null default current_timestamp,
    constraint ck_identity_dimension check (dimension in ('LIFECYCLE', 'ASSESSMENT', 'CAREER', 'CHANNEL')),
    constraint fk_ih_person foreign key (person_id) references person (id) on delete restrict
);

create index idx_ih_person on identity_history (person_id);

-- 4) 学期注册与考核（FR-04）。未配置阈值时只登记，不自动除名（I-23）。
create table semester_registration (
    id           uuid                     not null primary key,
    person_id    uuid                     not null,
    semester_id  uuid                     not null,
    status       varchar(16)              not null default 'REGISTERED',
    assessment   varchar(16)              not null default 'PENDING',
    note         varchar(500),
    assessed_by  uuid,
    assessed_at  timestamp with time zone,
    created_at   timestamp with time zone not null default current_timestamp,
    updated_at   timestamp with time zone not null default current_timestamp,
    constraint uq_registration_person_semester unique (person_id, semester_id),
    constraint ck_registration_status check (status in ('PENDING', 'REGISTERED', 'DECLINED')),
    constraint ck_registration_assessment check (assessment in ('PENDING', 'QUALIFIED', 'OBSERVING')),
    constraint fk_reg_person foreign key (person_id) references person (id) on delete restrict,
    constraint fk_reg_semester foreign key (semester_id) references semester (id) on delete restrict
);

create index idx_reg_semester on semester_registration (semester_id, assessment);

-- 5) 通用审批实例与节点（FR-29）。节点只追加；实例状态推进由服务层状态机约束。
create table approval_instance (
    id            uuid                     not null primary key,
    biz_type      varchar(32)              not null,
    biz_id        uuid                     not null,
    title         varchar(200)             not null,
    status        varchar(16)              not null default 'PENDING',
    current_level integer                  not null default 1,
    total_levels  integer                  not null default 1,
    created_by    uuid,
    created_at    timestamp with time zone not null default current_timestamp,
    updated_at    timestamp with time zone not null default current_timestamp,
    constraint ck_approval_status check (status in ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    constraint ck_approval_levels check (total_levels >= 1 and current_level >= 1 and current_level <= total_levels)
);

create index idx_approval_biz on approval_instance (biz_type, biz_id);

create table approval_node (
    id                  uuid                     not null primary key,
    instance_id         uuid                     not null,
    level               integer                  not null,
    assignee_person_id  uuid                     not null,
    decision            varchar(16)              not null default 'PENDING',
    comment             varchar(1000),
    decided_at          timestamp with time zone,
    created_at          timestamp with time zone not null default current_timestamp,
    constraint uq_approval_node unique (instance_id, level, assignee_person_id),
    constraint ck_approval_decision check (decision in ('PENDING', 'APPROVE', 'REJECT', 'RETURN')),
    constraint fk_an_instance foreign key (instance_id) references approval_instance (id) on delete restrict,
    constraint fk_an_assignee foreign key (assignee_person_id) references person (id) on delete restrict
);

create index idx_approval_node_assignee on approval_node (assignee_person_id, decision);

-- 6) 站内通知（FR-29）。P0 唯一渠道。
create table notification (
    id                  uuid                     not null primary key,
    recipient_person_id uuid                     not null,
    type                varchar(32)              not null,
    title               varchar(200)             not null,
    body                varchar(1000),
    biz_type            varchar(32),
    biz_id              uuid,
    read_at             timestamp with time zone,
    created_at          timestamp with time zone not null default current_timestamp,
    constraint fk_notification_recipient foreign key (recipient_person_id) references person (id) on delete restrict
);

create index idx_notification_recipient on notification (recipient_person_id, read_at);

-- 7) 奖惩记录（FR-05 摘要）。只追加，不提供删改端点。
create table discipline_record (
    id          uuid                     not null primary key,
    person_id   uuid                     not null,
    kind        varchar(16)              not null,
    title       varchar(200)             not null,
    detail      varchar(2000),
    decided_by  uuid,
    decided_at  timestamp with time zone not null default current_timestamp,
    created_at  timestamp with time zone not null default current_timestamp,
    constraint ck_discipline_kind check (kind in ('REWARD', 'PUNISH')),
    constraint fk_discipline_person foreign key (person_id) references person (id) on delete restrict
);

create index idx_discipline_person on discipline_record (person_id);