# B1 实施计划（FR-01..05 / FR-29）

- **版本**：1.0（2026-11-26，H2.0 通过后制定）
- **批 gate**：AT-01（入会双路径 + 学号唯一）、AT-02（任职/任期结束权限调整、历史保留）、AT-10（低出勤提醒不除名；普通/集训队退出分别处理）、审批退回可重提。
- **依赖**：B0（WP-P0-02..05，已 verified）。

> 说明：仓库内仅有《需求规格说明书》的条款摘要（总纲 §3.1、TRACEABILITY FR 行）。本文件把摘要落成可执行切片；凡摘要未定义的细节，按"最小闭环 + 配置化 + 不自动处罚"处理，并在 §6 登记待确认项，不擅自扩范围。

## 1. 工作包与顺序

| WP | 能力 | 依赖 | 验收 |
|---|---|---|---|
| WP-P0-06 | 组织：部门/岗位/届次/学期维护；任职与任期；到期失权 | 04 | AT-02：任职结束→权限立即失效，任职历史仍可查 |
| WP-P0-07 | 会员档案：一人一档、分类查询、身份变更留史 | 06 | FR-03：学号唯一；按部门/身份/年级/状态筛选；变更生成历史 |
| WP-P0-24 | 审批与通知：N 级待办、通知、结果、退回重提 | 04 | FR-29：待办可查、退回后新版本可重提、结果通知收件人可读 |
| WP-P0-08 | 入会申请：双路径、7 态、版本快照、审核轨迹 | 24 | AT-01：竞赛类/综合管理类分别进入审核；驳回/退回/通过留人/时间/意见 |
| WP-P0-09 | 学期注册与考核：注册 + 三态考核 + 观察名单 | 07 | AT-10（前半）：未配置阈值不自动除名；观察名单只读提示 |
| WP-P0-10 | 退出：普通/集训队分流程、告知期提示、生效动作、奖惩记录 | 09 | AT-10（后半）：两流程分别处理；审批通过≠生效，生效单独记录 |

## 2. 数据模型增量（Flyway V3，H2/PG 共同语法）

- `membership_application` 增列：`channel varchar(16) check in ('COMPETITION','GENERAL')`（申请路径，FR-02 双路径）；`effective_at timestamp with time zone`（DOM-08 生效时间）。
- `position_assignment`：person/position/department/term、start_date/end_date、status（ACTIVE/ENDED）、role_assignment_id、reason、granted_by/ended_by/ended_at、active_key（唯一，非空=person_id，结束置空）。
- `identity_history`：person_id、dimension（LIFECYCLE/ASSESSMENT/CAREER/CHANNEL）、from_value/to_value、reason、changed_by、changed_at。
- `semester_registration`：person_id、semester_id、status（PENDING/REGISTERED/DECLINED）、assessment（PENDING/QUALIFIED/OBSERVING）、assessed_by/assessed_at/note、timestamps；uq(person_id, semester_id)。
- `approval_instance`：biz_type、biz_id、title、status（PENDING/APPROVED/REJECTED/CANCELLED）、current_level、total_levels、created_by/created_at。
- `approval_node`：instance_id、level、assignee_person_id、decision（PENDING/APPROVE/REJECT/RETURN）、comment、decided_at、created_at；uq(instance_id, level, assignee_person_id)。
- `notification`：recipient_person_id、type、title、body、biz_type、biz_id、read_at、created_at；索引(recipient_person_id, read_at)。
- 奖惩（FR-05 摘要含奖惩）：`discipline_record`：person_id、kind（REWARD/PUNISH）、title、detail、decided_at、decided_by、created_at（只追加，不删改）。

## 3. 状态机与不变量

- 申请：DRAFT→SUBMITTED→PROCESSING→(APPROVED|REJECTED|RETURNED)；RETURNED→SUBMITTED（新版本）；DRAFT/SUBMITTED→WITHDRAWN。仅 DRAFT 可编辑；仅 SUBMITTED/PROCESSING 可审核；终态不可迁移（DOM-07）。
- 生效：APPROVED 仅写 `effective_at`；JOIN 生效生成/续接 membership（PROBATION+TEAM_MEMBER/LAB_MEMBER），EXIT 生效置 membership lifecycle=EXITED、active_key=null，并保留历史（DOM-08/11）。
- 退出告知：集训队（channel=COMPETITION）按 `exit.notice_workdays` 计算应提前日期，仅返回提示字段，不阻断、不自动处罚（BR-04 + I-23）。
- 考核：`attendance.min_rate` 等 5 个 MANUAL 规则为空时不计算、不自动除名；观察名单仅查询视图（AT-10）。
- 禁自批：审核人=申请人 → 403 SELF_APPROVAL_DENIED（I-07/DENY-05）。
- 审批通过≠生效：`POST /applications/{id}/effective` 单独动作并审计。

## 4. API 切片

- 组织：`GET/POST /api/v1/org/departments|positions|terms`（org:read/org:write）；`GET/POST /api/v1/org/position-assignments`、`POST /{id}/end`（position:assign）。
- 档案：`GET /api/v1/members`（member:read，按 scope 过滤 department/self）、`GET /api/v1/members/{personId}`、`PATCH /{personId}/identity`（member:write）。
- 审批：`GET /api/v1/approvals/todo`、`GET /{id}`、`POST /{id}/act`（approval:act）；`GET /api/v1/notifications`、`POST /{id}/read`、`POST /api/v1/notifications/read-all`。
- 申请：`POST /api/v1/applications`、`GET /mine`、`GET /{id}`、`POST /{id}/submit|withdraw|effective`、`POST /{id}/review`（application:review）。
- 注册考核：`POST /api/v1/registrations`、`GET /mine`、`GET /api/v1/registrations`（member:read scoped）、`POST /{id}/assess`（member:assess）、`GET /api/v1/registrations/observing`。
- 奖惩：`GET/POST /api/v1/members/{personId}/disciplines`（member:discipline）。

## 5. 测试（每个 WP 至少 1 正 1 反）

- `OrgTermTest`：任职→权限生效；end 后同 token 立即 403/权限消失；历史行仍可查；非 SYSADMIN/OFFICE 无 position:assign。
- `MemberProfileTest`：一人一档；重复学号 409；部门过滤；身份变更写 identity_history 并审计。
- `ApprovalFlowTest`：两级审批逐级通过；退回→新版本重提→再通过；自批 403；越权 act 403。
- `ApplicationFlowTest`：AT-01 双路径；7 态合法/非法迁移；版本快照不可改；审核轨迹含 reviewer/time/comment。
- `RegistrationTest`：AT-10 前半；MANUAL 未配置不除名；观察名单只出现在观察者；评分需 member:assess。
- `ExitTest`：AT-10 后半；普通/集训队流程不同（告知期提示字段）；审批通过后需生效动作；生效后 membership EXITED 且 active_key 为空。

## 6. 待确认项（登记，不阻塞实施）

- Q-02 观察会员判定口径（当前：assessment=OBSERVING 即观察名单，人工评定）。
- 考核"三态"具体枚举以 ADR-0002 的 PENDING/QUALIFIED/OBSERVING 落地；若需求另有"不合格"态，需变更单。
- 普通退出是否需要审议会（当前按单级审批 + 审计留痕实现）。
- 通知渠道（站内信为 P0 唯一渠道；短信/邮件待 H5 扩展审批）。