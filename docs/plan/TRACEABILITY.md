# 需求追溯矩阵（TRACEABILITY）

- **版本**：1.3（B2 算法目录/题目与标签/导入框架/训练计划回写，2026-11-26）
- **权威来源**：《需求规格说明书》FR-01..44、BR-01..21、NFR-01..08、AT-01..14
- **维护规则**：每个 WP 合并前更新本表；`状态` 只能由验证人改为 `verified`；代码/测试列填仓库相对路径（GOV-04）。

## 0. 状态图例

| 状态 | 含义 |
|---|---|
| `planned` | 已排入 WP，未开工 |
| `in_progress` | 正在实现 |
| `implemented` | 代码完成，测试未全绿 |
| `verified` | 验收断言全部通过，可演示 |
| `deferred` | P1/P2 暂缓或未排期 |
| `blocked` | 依赖未确认问题，只能做安全骨架 |

## 1. 功能需求（44 条）

| 需求 | 优先级 | 验收要点（摘要） | WP | 代码 | 测试 | 状态 |
|---|---|---|---|---|---|---|
| FR-01 | P0 | 部门/岗位/任期/授权；任期结束权限失效，历史可查 | WP-P0-06 | `org/OrgService.java`、`org/PositionAssignment.java` | `org/OrgAssignmentTest` | implemented |
| FR-02 | P0 | 入会双路径；每次审核留人/时间/意见 | WP-P0-08 | `application/ApplicationService.java`、`application/ApplicationReview.java` | `application/ApplicationFlowTest` | implemented |
| FR-03 | P0 | 会员档案与分类查询；学号不重复；身份变更留史 | WP-P0-07 | `member/MemberService.java`、`member/IdentityHistory.java` | `application/ApplicationFlowTest`、`member/RegistrationTest` | implemented |
| FR-04 | P0 | 学期注册、考核、观察名单、恢复；未配置不自动除名 | WP-P0-09 | `member/RegistrationService.java`、`member/SemesterRegistration.java` | `member/RegistrationTest` | implemented |
| FR-05 | P0 | 退出/告知/审议/奖惩；普通与集训队流程不同 | WP-P0-10 | `application/ApplicationService.java`（effect/validateExitNotice）、`member/DisciplineRecord.java` | `application/ExitFlowTest` | implemented |
| FR-06 | P0 | 知识点分层、难度、关联题目；停用可追溯 | WP-P0-11 | `category/CategoryService.java`、`category/AlgorithmCategory.java` | `category/CategoryTest`（停用/合并/迁移） | implemented |
| FR-07 | P0 | 题目字段完整；同来源题号重复提示；多知识点 | WP-P0-12 | `problem/ProblemService.java`、`problem/Problem.java` | `problem/ProblemTest`（字段/重复 409） | implemented |
| FR-08 | P1 | 题单/题解/资料归档并按对象分配，保留发布版本 | P1-题库 | — | — | deferred |
| FR-09 | P0 | 学期/寒假/暑假计划；目标/频次/时长/负责人；审批发布 | WP-P0-14 | `trainingplan/TrainingPlanService.java`、`trainingplan/TrainingPlan.java` | `trainingplan/TrainingPlanTest` | implemented |
| FR-10 | P0 | 个人规划与薄弱点任务；月度调整留前后版本 | WP-P0-15 | — | — | planned |
| FR-11 | P0 | 观察/新生/非观察/队伍差异化任务；个人与团队不重复计分 | WP-P0-15 | — | — | planned |
| FR-12 | P0 | 签到/请假/补录/完成/错题；修改留原因 | WP-P0-16 | — | — | planned |
| FR-13 | P1 | 能力测评与雷达图；证据可追溯；缺数据标未评估 | P1-能力 | — | — | deferred |
| FR-14 | P1 | 月度/学期考核、评优、提醒；绑定规则版本 | P1-考核 | — | — | deferred |
| FR-15 | P0 | 新生课程/讲师/课件/录屏/签到/反馈/总结 | WP-P0-17 | — | — | planned |
| FR-16 | P1 | 新生综合评价、拔高/普及分组、师徒互选；>3 阻断 | P1-培养 | — | — | deferred |
| FR-17 | P1 | 新生选拔赛、潜力档案、进阶推荐 | P1-培养 | — | — | deferred |
| FR-18 | P0 | 寒假按周任务；新生第一学期 4 周；提交/答疑/完成率 | WP-P0-18 | — | — | planned |
| FR-19 | P0 | 组队/队长/沟通/审批/公示/调整；正式 3 人；阵容留史 | WP-P0-19 | — | — | planned |
| FR-20 | P1 | 团队分工、队长提醒、团队能力报告 | P1-团队 | — | — | deferred |
| FR-21 | P0 | 暑训名单/队伍/多校/每日任务/周安排；区分年级 | WP-P0-20 | — | — | planned |
| FR-22 | P1 | 模拟赛/赛前会/赛后复盘/规则检查清单 | P1-团队 | — | — | deferred |
| FR-23 | P0 | 校赛计划/报名/名单/成绩/排名/奖项/总结；导出导入 | WP-P0-21 | — | — | planned |
| FR-24 | P0 | 非 XCPC 赛事配置化报名（个人/团队、字段、截止） | WP-P0-22 | — | — | planned |
| FR-25 | P1 | 补正/撤回/通知/外部结果；校内与主办方状态分开 | P1-报名 | — | — | deferred |
| FR-26 | P0 | 赛季/预选/报名/成绩/证据；同赛季多场；成员快照 | WP-P0-23 | — | — | planned |
| FR-27 | P1 | 内部排序/名额/选择；公式未定只能人工 | P1-排序 | — | — | deferred |
| FR-28 | P1 | 正赛备战/每周模拟/赛站资料/成绩奖项总结 | P1-备战 | — | — | deferred |
| FR-29 | P0 | 审批待办/通知/提醒/结果/归档；退回可重提 | WP-P0-24 | `approval/ApprovalService.java`、`notification/NotificationService.java` | `application/ApplicationFlowTest`（待办/退回重提/通知） | implemented |
| FR-30 | P0 | 按学期/年级/状态/部门/队伍统计与导出；同权限+审计 | WP-P0-25 | — | — | planned |
| FR-31 | P2 | 财务账簿/凭证/报销/双审批/公示 | P2-财务 | — | — | deferred |
| FR-32 | P2 | 资产台账/保管/维护/盘点/交接 | P2-资产 | — | — | deferred |
| FR-33 | P2 | 对外活动策划/审核/校方批准/总结 | P2-活动 | — | — | deferred |
| FR-34 | P2 | 会议/换届/公示/表决/备案 | P2-治理 | — | — | deferred |
| FR-35 | P2 | MBTI 记录与展示；自愿、限可见、不作资格依据 | P2-MBTI | — | — | deferred |
| FR-36 | P2 | 薄弱项推荐训练题；解释依据、人工可调 | P2-推荐 | — | — | deferred |
| FR-37 | P0 | 算法大类/子类/知识点三级目录；增改排序停用合并 | WP-P0-11 | `category/CategoryService.java`（MAX_DEPTH=3、merge） | `category/CategoryTest.categoryTreeAllowsThreeLevelsAndRejectsFourth_DOM18`、`mergeMovesProblemsAndKeepsAlias_DOM21` | implemented |
| FR-38 | P0 | 分类属性：简称/说明/阶段/建议难度/排序/负责人 | WP-P0-11 | `category/AlgorithmCategory.java`（short_name/description/stage/suggested_difficulty/owner_person_id/sort_order）、`category/CategoryAttribute.java` | `category/CategoryTest.attributeUpsertValidatesValueType_DOM18` | implemented |
| FR-39 | P1 | 前置关系与学习路线；阻止循环依赖 | P1-题库 | — | — | deferred |
| FR-40 | P0 | 标签区分主/辅算法、题型、技巧、年份、级别；恰一主算法 | WP-P0-12 | `problem/ProblemService.java`（PRIMARY_ALGORITHM_REQUIRED/MULTIPLE_PRIMARY_ALGORITHMS）、`problem/Tag.java` | `problem/ProblemTest`（双主/无主/改主） | implemented |
| FR-41 | P1 | 题目批量导入/导出/去重/校验；错误行可下载重传 | P1-题库 | — | — | deferred |
| FR-42 | P1 | 题目审核/纠错/下架/版本；未审核不进正式题单 | P1-题库 | — | — | deferred |
| FR-43 | P1 | 按算法统计题量/难度/覆盖率/完成率/薄弱点；可下钻 | P1-统计 | — | — | deferred |
| FR-44 | P1 | 多维组合检索；结果正确；常用筛选可保存 | P1-题库 | — | — | deferred |

## 2. 业务规则（21 条）

| 规则 | 系统处理 | 落地 WP | 测试断言 | 状态 |
|---|---|---|---|---|
| BR-01 | 按学期生成注册与考核记录，支持观察名单 | WP-P0-09 | 注册唯一 + 观察状态流转 | implemented |
| BR-02 | 连续不合格次数未确认：只生成待审名单，保留决定 | WP-P0-09 | 未配置时不自动除名 | implemented（`RegistrationService` 只记录结论，不改生命周期） |
| BR-03 | 三次缺勤/出勤率 50% 未确认：只提醒 + 人工审议 | WP-P0-16 | 低出勤提醒不改变资格 | planned |
| BR-04 | 集训队退出提前 15 个工作日校验，审批链可配置 | WP-P0-10 | 不足 15 日阻断提交并说明 | implemented（`validateExitNotice`；普通会员不阻断） |
| BR-05 | 开学 2 周内完成计划，按业务日历计算 | WP-P0-14 | `plan.submit_deadline_weeks`=2（BLOCK）：超期提交 400 PLAN_SUBMIT_DEADLINE_PASSED | implemented（节假日/调休日历待 P0-24） |
| BR-06 | 培训 ≥8 次、每次 ≥90 分钟；寒假 4 周；第二学期 12 周 | WP-P0-17/18 | 次数与时长分别统计 | planned |
| BR-07 | 培训结束 3 个工作日内反馈/总结提醒 | WP-P0-17 | 到期提醒 + 逾期状态 | planned |
| BR-08 | 拔高组约 15 人，允许说明调整原因 | WP-P0-17 | 规模提示不阻断 | planned |
| BR-09 | 师傅 ≤3 人阻断；普及 5–7 人仅提示 | WP-P0-17 | 第 4 人阻断；4/8 人提示 | planned |
| BR-10 | 指导小组 2–3 名干事，可多人指导 | WP-P0-17 | 多人指导关系有效 | planned |
| BR-11 | 选拔赛 3 个工作日内排名与总结 | WP-P0-17 | 成绩/总结分别计时 | planned |
| BR-12 | 大一暑假前能力测评；每月复盘 | P1-能力 | 名单与月度任务生成 | deferred |
| BR-13 | XCPC 正式队 3 人；草稿可不满员 | WP-P0-19 | 2 人确认失败、3 人成功 | planned |
| BR-14 | 暑训周一至周六，周日休；周六下午集体活动 | WP-P0-20 | 日历模板生成正确 | planned |
| BR-15 | 高年级多校、新生可只牛客；混合队待确认 | WP-P0-20 | 按阶段校验报名 | planned |
| BR-16 | 暑训起止由负责人确认，不预置日期 | WP-P0-20 | 空日期不允许发布 | planned |
| BR-17 | 正赛队每周 ≥3 次模拟赛；请假例外待确认 | P1-备战 | 未达标提醒不处罚 | deferred |
| BR-18 | 月度与学期考核分别出结果 | P1-考核 | 两类周期独立 | deferred |
| BR-19 | 单笔 >500 元会长 + 指导老师双审批 | P2-财务 | 500 不触发、501 触发 | deferred |
| BR-20 | 财务公示 ≥3 工作日；活动总结 3 工作日 | P2-财务 | 公示记录与提醒 | deferred |
| BR-21 | 分类被引用只能停用/合并；每题至少一主算法 | WP-P0-11/12 | 无删除端点；停用有子 409 CATEGORY_HAS_CHILDREN；合并迁移题目；每题恰一主算法（400） | implemented |

## 3. 验收场景（14 个）

| AT | 场景 | 主要 FR | WP | E2E | 状态 |
|---|---|---|---|---|---|
| AT-01 | 竞赛类/综合管理类入会申请分别处理，学号不重复 | FR-02..04 | WP-P0-08 | e2e/at-01.spec.ts（服务端：`ApplicationFlowTest`） | in_progress |
| AT-02 | 会员+任职+队长，任期结束后权限调整、历史保留 | FR-01/03/19 | WP-P0-06 | e2e/at-02.spec.ts（服务端：`OrgAssignmentTest`） | in_progress |
| AT-03 | 多算法周任务 + 重复/待核实成绩；按三级下钻 | FR-06..12/37..44 | WP-P0-15 | e2e/at-03.spec.ts | planned |
| AT-04 | 新生培训+寒假任务；次数/时长/周数/反馈分别检查 | FR-15/18 | WP-P0-17 | e2e/at-04.spec.ts | planned |
| AT-05 | 第 4 名同期徒弟被阻断 | FR-16 | WP-P0-17 | e2e/at-05.spec.ts | planned |
| AT-06 | 未满 3 人只能草稿；补齐后确认；历史阵容不变 | FR-19/26 | WP-P0-19 | e2e/at-06.spec.ts | planned |
| AT-07 | 暑训报名与训练安排；年级/日历分别校验 | FR-21 | WP-P0-20 | e2e/at-07.spec.ts | planned |
| AT-08 | 校内通过但无外部回执：显示"待外部确认" | FR-24/25 | WP-P0-22 | e2e/at-08.spec.ts | planned |
| AT-09 | 名额分配不超额；排序依据与调整可追溯 | FR-27 | P1-排序 | e2e/at-09.spec.ts | deferred |
| AT-10 | 低出勤提醒不除名；普通/集训队退出分别处理 | FR-05 | WP-P0-10 | e2e/at-10.spec.ts（服务端：`ExitFlowTest`、`RegistrationTest`） | in_progress |
| AT-11 | 普通会员/队长/离任部长越权读取与下载被拒 | NFR-02/03 | WP-P0-26 | e2e/at-11.spec.ts（B0 服务端负向：AuthorizationTest / FileAccessTest） | in_progress |
| AT-12 | 考勤修订/成绩导入/导出/恢复备份；日志回执完整 | FR-12/30 | WP-P0-25 | e2e/at-12.spec.ts（B0 审计部分：AuditAndRuleTest） | in_progress |
| AT-13 | 三级目录+前置无环+导入重复/错误；每题一主算法 | FR-37..42 | WP-P0-11/12/13 | e2e/at-13.spec.ts（服务端：`CategoryTest`/`ProblemTest`/`ImportTest`） | in_progress（B2 服务端已交付） |
| AT-14 | 多维检索与算法覆盖统计，可下钻到有效完成 | FR-43/44 | P1-统计 | e2e/at-14.spec.ts | deferred |

## 4. 非功能需求（8 条）

| NFR | 要求 | 落地 | 状态 |
|---|---|---|---|
| NFR-01 | 桌面管理 + 手机报名/签到/任务/反馈 | Web 响应式 + P0-16 | planned |
| NFR-02 | 服务端按角色与数据范围校验；停用即时失效 | WP-P0-03/04/26；`JwtAuthenticationFilter`、`ScopeGuard`、`AuthService` | in_progress（B0 服务端已交付，E2E 待 WP-P0-26） |
| NFR-03 | 敏感信息按需展示、限制批量导出、密码不明文 | WP-P0-03/25；Argon2id、refresh 仅 HttpOnly、`FileService` 可见性 | in_progress（密码/令牌已满足，导出限制待 P0-25） |
| NFR-04 | 审批/状态/权限/成绩/考勤/导出可审计 | WP-P0-05；`AuditService` + PG V2 触发器 | in_progress（B1 申请/审核/生效/任职/档案/注册已接入审计与通知） |
| NFR-05 | 一致性校验、导入预检、错误行与回执 | WP-P0-13；`importing/ImportService.java`（PREVIEW/APPLIED/FAILED、row_error、错误 CSV） | implemented |
| NFR-06 | 定期备份并验证恢复 | WP-P0-02/25 | planned |
| NFR-07 | 规则/学期/日历/赛事/难度/审批人可配置 | WP-P0-02/24 | planned |
| NFR-08 | 性能目标按真实规模评审后压测 | G1 前 | planned |

## 5. 覆盖统计（G0 基线）

| 项 | 总数 | P0 | P1 | P2 | verified |
|---|---|---|---|---|---|
| FR | 44 | 23 | 15 | 6 | 0 |
| BR | 21 | 15 | 4 | 2 | 0 |
| AT | 14 | 10 | 4 | 0 | 0 |
| NFR | 8 | 8 | 0 | 0 | 0 |

> P0 的 23 条 FR 与 GOV-001 §8 一致；P1/P2 在本表用 `deferred` 明确"未交付"，不得以 P0 验收宣称全量完成。
> B2 交付后新增 `implemented`：FR-06/07/09/37/38/40、NFR-05、BR-05/21（B1 的 `implemented` 见上表）。`verified` 仍为 0，须经 H2.2 人工评审与 AT 端到端后才可改为 `verified`。
## 6. B0 基础设施条款对照（2026-11-26）

| 条款 / 需求 | 落地位置 | 验证 | 状态 |
|---|---|---|---|
| DOM-01 学号唯一 | `db/migration/common/V1__core.sql`（`uq_person_student_no`） | 迁移成功；`SeedIdempotencyTest` | implemented |
| DOM-06 服务端鉴权 | `config/SecurityConfig.java`、各 Controller `@PreAuthorize`、`security/ScopeGuard.java` | `AuthorizationTest`、`RoutePermissionTest` | implemented |
| DOM-11 业务记录不物理删除 | V1 全部 FK `on delete restrict`；`file_object.archived_at`、`role_assignment.revoked_at`；无删除 API | 代码评审 + `FileAccessTest` 归档用例 | implemented |
| DOM-12 阈值配置化 | `rule_config` + `rule/RuleService.java` | `AuditAndRuleTest`、`SeedIdempotencyTest` | implemented |
| DOM-27 审计字段 | `audit/AuditLog.java`、`AuditService`（actor/时间/IP/对象/前后快照/原因/requestId） | `AuditAndRuleTest` | implemented |
| TECH-08 RBAC + 数据范围 | `role`、`role_permission`、`role_assignment`（SELF/TEAM/DEPARTMENT/GLOBAL） | `SeedIdempotencyTest`（11 角色）、`AuthorizationTest` | implemented（TEAM 判定待 B1 队伍模型） |
| TECH-09 权限码 `模块:动作` | `security/PermissionCode.java`、`packages/shared/src/permissions.ts` | `node scripts/check-permissions.mjs` → 65 个一致 | implemented |
| TECH-10 路由-权限元测试 | `src/test/.../security/RoutePermissionTest.java` | 24 测试全绿 | implemented |
| TECH-11 数据范围注入查询 | `security/ScopeGuard.java`、`audit/AuditService.java`（非 GLOBAL 只查本人） | `AuthorizationTest` | implemented（B0 域有限） |
| TECH-12 会话与即时失效 | `security/JwtService.java`、`auth/AuthService.java`、`auth/LoginAttemptService.java` | `AuthFlowTest` 7 例 | implemented |
| TECH-13 附件与签名下载 | `file/FileService.java`、`file/FileTokenService.java`、`file/FileController.java` | `FileAccessTest` 3 例 | implemented |
| DATA-05 / I-21 审计只增不改 | PG `V2__audit_append_only.sql` 触发器；无写改删端点 | `AuditAndRuleTest`（无 DELETE 路由）；触发器待 PG 实库验证 | implemented |
| I-23 / DENY-04 未确认阈值只提示 | 5 个 `MANUAL` 空值规则键；`RuleService` 拒绝 MANUAL 带值 | `AuditAndRuleTest`、`SeedIdempotencyTest` | implemented |
| DENY-11 / DENY-12 密钥与日志 | 密钥仅环境变量（prod 强制）、错误响应不含堆栈/凭据 | `GlobalExceptionHandler`；`application-prod.yml` | implemented |


## 7. B1 条款对照（2026-11-26）

| 条款 / 需求 | 落地位置 | 验证 | 状态 |
|---|---|---|---|
| DOM-07 申请 7 态与审批轨迹 | `application/ApplicationStatus.java`、`ApplicationService`、`ApplicationVersion`（快照）、`ApplicationReview`（只追加） | `ApplicationFlowTest`（退回→v2→重提→通过） | implemented |
| D-05 审批通过 ≠ 生效 | `ApplicationService.effect`（APPROVED 后由 member:write 确认，写入 effectedAt 才建会员） | `ApplicationFlowTest`（审批后 membership 为空，生效后才 PROBATION） | implemented |
| I-07 申请人不得自批 | `ApprovalService.decide`（actor==申请人 → 403 SELF_APPROVAL_DENIED）+ `ScopeGuard.assertNotSelf` | `ApplicationFlowTest.applicantCannotApproveOwnApplication` | implemented |
| DOM-12 / I-23 未确认阈值只提示 | `validateExitNotice` 用 `rule_config.exit.notice_workdays`；考核/缺勤阈值仍 MANUAL | `ExitFlowTest`、`RegistrationTest` | implemented |
| DOM-15 历史不可改写 | `identity_history`、`approval_node`、`application_review` 只追加；无删除入口 | 代码评审 + 各测试断言状态不回退 | implemented |
| FR-01 组织任期与到期失权 | `org/OrgService.assign/end`（结束任职回收角色并递增 token_version） | `OrgAssignmentTest`（结束任职后旧令牌 401） | implemented |
| FR-02 入会申请双路径 | `channel`（COMPETITION/GENERAL）持久化；审批人按 `application:review` 通知 | `ApplicationFlowTest`（channel=COMPETITION） | implemented（路径分流规则待 Q 确认） |
| FR-03 一人一档与身份变更留史 | `MemberService.profile`（含终态成员回看最近身份）、`IdentityHistory` | `ApplicationFlowTest`、`ExitFlowTest` | implemented |
| FR-04 学期注册与考核三态 | `RegistrationService`（幂等注册、三态校验、同步 membership.assessment） | `RegistrationTest` | implemented |
| FR-05 退出与告知期 | `ApplicationService.effect`（EXIT→EXITED+exitedAt+exitReason）、`validateExitNotice` | `ExitFlowTest` 3 例 | implemented |
| FR-29 审批与通知 | `ApprovalService`（N 级、待办、退回）、`NotificationService`（站内信） | `ApplicationFlowTest`（todo/通知/退回重提） | implemented |

## 8. B2 条款对照（2026-11-26）

| 条款 / 需求 | 落地位置 | 验证 | 状态 |
|---|---|---|---|
| DOM-18 三级目录、同级唯一、排序/停用/合并 | `db/migration/common/V4__b2_problem_plan.sql`（`name_key` 唯一、`stage` 1..3）、`category/CategoryService.java`（MAX_DEPTH=3、nameKey=`parentId|name`） | `CategoryTest.categoryTreeAllowsThreeLevelsAndRejectsFourth_DOM18`、`sameSiblingNameRejectedButDifferentParentsAllowed_DOM18` | implemented |
| DOM-19 每题恰一主算法 + 多标签 | `problem/ProblemService.java`（主算法唯一性校验、`primary_key` 可空唯一）、`problem/Tag.java`（TYPE/SKILL/YEAR/LEVEL） | `ProblemTest.createRequiresExactlyOnePrimaryAlgorithm_DOM19`、`updateMovesPrimaryAlgorithmAndKeepsSinglePrimary_DOM19`、`tagDictionaryValidatesTypeAndInactiveReuse_DOM19` | implemented |
| DOM-20 前置关系保存时拒环 | `category/CategoryService.addRelation`（自环 400、DFS `reaches` 成环 409）、`category_relation` 唯一对 + 非自引用 check | `CategoryTest.relationGraphRejectsSelfAndCycle_DOM20` | implemented（`FR-39` 学习路线仍 deferred） |
| DOM-21 被引用分类只能停用/合并 | 无删除端点；`deactivate` 有启用子节点 409 `CATEGORY_HAS_CHILDREN`；`merge` 迁移 `problem_algorithm` 并留 `merged_into_id` | `CategoryTest.deactivateKeepsRowAndBlocksNewChildren_DOM21`、`mergeMovesProblemsAndKeepsAlias_DOM21`、`mergeRejectsNonSiblingTarget_DOM21` | implemented |
| DOM-22 未审核题不进正式题单 | B2 未建题单模型（FR-08 P1 deferred）；`problem.active` 已隔离停用题 | `ProblemTest.deactivateExcludesProblemFromActiveList_DOM11` | planned（题单 P1 实现时接入 `problem:review`） |
| DOM-23 同来源+题号重复必须提示 | `problem` 表 `uq_problem_source_title(source_key,title_key)` + `ProblemService` 预检 409 `PROBLEM_DUPLICATE`（提示而非静默覆盖） | `ProblemTest.duplicateSourceAndTitleRejected_DOM23` | implemented |
| DOM-24 导入两阶段 + 错误行可下载 + 幂等 | `importing/ImportService.java`（PREVIEW→APPLIED、`import_row_error` 只追加）、`ImportController`（`/preview`、`/{id}/apply`、`/{id}/errors.csv`） | `ImportTest.previewReportsRowErrorsWithoutWritingBodies_DOM24`、`applyRejectedWhenPreviewHasErrors_DOM24`、`guardsFileKindAndHeader_DOM24` | implemented（仅 MEMBER 开放，见 §8 偏差） |
| DOM-10 计划状态机与发布版本 | `trainingplan/TrainingPlanService.java`（DRAFT→SUBMITTED→APPROVED→PUBLISHED，`training_plan_version` 快照） | `TrainingPlanTest.submitApprovePublishLifecycle_DOM10`、`editCreatesVersionSnapshotAndValidatesContent_DOM10`、`reviseAfterPublishCreatesNewVersionAndKeepsHistory_DOM10` | implemented |
| DOM-11 逻辑删除 | `problem.active`、`tag.active`、`algorithm_category.active`；无 DELETE API | `ProblemTest.deactivateExcludesProblemFromActiveList_DOM11` | implemented |
| DOM-12 阈值配置化 | `TrainingPlanService.submit` 只读 `rule_config.plan.submit_deadline_weeks`（缺省 2），不硬编码 | `TrainingPlanTest.semesterSubmitDeadlineComesFromRuleConfig_BR05` | implemented |
| DOM-15 历史不可改写 | `training_plan_version` 只追加、`import_row_error` 只追加、审批轨迹复用 `approval_node` | 各测试断言发布后拒绝编辑（409 `PLAN_PUBLISHED_IMMUTABLE`） | implemented |
| DATA-05 不变量双保险 | DB：`uq_problem_source_title`、`uq_problem_algorithm_primary`、`ck_problem_algorithm_primary`、`uq_category_relation`；服务：先校验后写 | `ProblemTest`、`CategoryTest`（违反唯一/主算法） | implemented |
| DATA-06 命名与审计列 | V4 全部 snake_case，新表含 `created_at`/`updated_at`（`import_row_error`、`training_plan_version` 只追加无 updated_at） | 迁移与实体一致（`category_relation.updated_at` 已补齐） | implemented |
| DATA-09 分页上限 100 | `ProblemService.list`、`TrainingPlanService.list` 对 `size` 截断 | `ProblemTest.filtersByCategoryTagAndKeywordWithCappedPageSize_DATA09` | implemented |
| DATA-11 导入幂等（同文件重复提交返回原结果） | `import_batch.file_sha256` + `kind` 查已有批次，`duplicate=true` 直接返回 | `ImportTest.applyIsIdempotentForSameFile_DATA11` | implemented |
| ENG-02 统一错误体 / ENG-03 分页体 | `GlobalExceptionHandler`（code/message/requestId）、`PageResponse`（items/page/size/total） | 各测试断言 `$.code` 与 `$.size` | implemented |
| TECH-11 每 API 至少 1 个 403 | 四个新模块均有负向用例 | `CategoryTest.categoryWriteRequiresCategoryWrite_TECH11`、`ProblemTest.problemWriteRequiresProblemWrite_TECH11`、`ImportTest.importRequiresImportWrite_TECH11`、`TrainingPlanTest.planWriteRequiresPlanWrite_TECH11` | implemented |
| 权限码四方一致 | `security/PermissionCode.java` 新增 `import:write`、`packages/shared/src/permissions.ts`、`packages/shared/src/labels.ts`（中文标签，`Record<PermissionCode,string>` 缺项会令前端 `tsc --noEmit` 失败）、`docs/security/权限矩阵.md` | `node scripts/check-permissions.mjs` → 65 个一致；`AuthFlowTest` 目录数 65 | implemented |
| BR-21 / AT-13 服务端部分 | 见上（DOM-19/20/21/24） | `CategoryTest` + `ProblemTest` + `ImportTest` 共 21 例 | implemented（E2E `e2e/at-13.spec.ts` 待 P1） |

已知偏差（须在 H2.2 评审确认）：
1. `training_plan.content`、`training_plan_version.snapshot` 由计划中的 `jsonb` 改为 `text`（H2 PostgreSQL 模式对 jsonb 绑定不稳定），由服务层 Jackson 序列化与校验。
2. V4 计划中的 `import_batch.file_object_id` 未建：原始导入文件不落库，`import_row_error` 保留错误明细，`errors.csv` 由错误明细重生成（同时避免留存个人信息副本）。
3. `ATTENDANCE`/`RESULT` 两类导入仅登记 kind 枚举，暂无 handler，调用返回 400 `IMPORT_KIND_NOT_SUPPORTED`；`MEMBER` 已开放。
4. `publish` 只改状态不写版本快照（内容未变，重复写会违反 `uq_training_plan_version`）；内容修订走 `POST /{id}/versions`。
5. `DOM-22`（未审核题不进正式题单）随 P1 题单模型实现，B2 只保证停用题不进入默认列表。