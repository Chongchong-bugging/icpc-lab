# 需求追溯矩阵（TRACEABILITY）

- **版本**：1.1（B0 基础设施回写，2026-11-26）
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
| FR-01 | P0 | 部门/岗位/任期/授权；任期结束权限失效，历史可查 | WP-P0-06 | — | — | planned |
| FR-02 | P0 | 入会双路径；每次审核留人/时间/意见 | WP-P0-08 | — | — | planned |
| FR-03 | P0 | 会员档案与分类查询；学号不重复；身份变更留史 | WP-P0-07 | — | — | planned |
| FR-04 | P0 | 学期注册、考核、观察名单、恢复；未配置不自动除名 | WP-P0-09 | — | — | planned |
| FR-05 | P0 | 退出/告知/审议/奖惩；普通与集训队流程不同 | WP-P0-10 | — | — | planned |
| FR-06 | P0 | 知识点分层、难度、关联题目；停用可追溯 | WP-P0-11 | — | — | planned |
| FR-07 | P0 | 题目字段完整；同来源题号重复提示；多知识点 | WP-P0-12 | — | — | planned |
| FR-08 | P1 | 题单/题解/资料归档并按对象分配，保留发布版本 | P1-题库 | — | — | deferred |
| FR-09 | P0 | 学期/寒假/暑假计划；目标/频次/时长/负责人；审批发布 | WP-P0-14 | — | — | planned |
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
| FR-29 | P0 | 审批待办/通知/提醒/结果/归档；退回可重提 | WP-P0-24 | — | — | planned |
| FR-30 | P0 | 按学期/年级/状态/部门/队伍统计与导出；同权限+审计 | WP-P0-25 | — | — | planned |
| FR-31 | P2 | 财务账簿/凭证/报销/双审批/公示 | P2-财务 | — | — | deferred |
| FR-32 | P2 | 资产台账/保管/维护/盘点/交接 | P2-资产 | — | — | deferred |
| FR-33 | P2 | 对外活动策划/审核/校方批准/总结 | P2-活动 | — | — | deferred |
| FR-34 | P2 | 会议/换届/公示/表决/备案 | P2-治理 | — | — | deferred |
| FR-35 | P2 | MBTI 记录与展示；自愿、限可见、不作资格依据 | P2-MBTI | — | — | deferred |
| FR-36 | P2 | 薄弱项推荐训练题；解释依据、人工可调 | P2-推荐 | — | — | deferred |
| FR-37 | P0 | 算法大类/子类/知识点三级目录；增改排序停用合并 | WP-P0-11 | — | — | planned |
| FR-38 | P0 | 分类属性：简称/说明/阶段/建议难度/排序/负责人 | WP-P0-11 | — | — | planned |
| FR-39 | P1 | 前置关系与学习路线；阻止循环依赖 | P1-题库 | — | — | deferred |
| FR-40 | P0 | 标签区分主/辅算法、题型、技巧、年份、级别；恰一主算法 | WP-P0-12 | — | — | planned |
| FR-41 | P1 | 题目批量导入/导出/去重/校验；错误行可下载重传 | P1-题库 | — | — | deferred |
| FR-42 | P1 | 题目审核/纠错/下架/版本；未审核不进正式题单 | P1-题库 | — | — | deferred |
| FR-43 | P1 | 按算法统计题量/难度/覆盖率/完成率/薄弱点；可下钻 | P1-统计 | — | — | deferred |
| FR-44 | P1 | 多维组合检索；结果正确；常用筛选可保存 | P1-题库 | — | — | deferred |

## 2. 业务规则（21 条）

| 规则 | 系统处理 | 落地 WP | 测试断言 | 状态 |
|---|---|---|---|---|
| BR-01 | 按学期生成注册与考核记录，支持观察名单 | WP-P0-09 | 注册唯一 + 观察状态流转 | planned |
| BR-02 | 连续不合格次数未确认：只生成待审名单，保留决定 | WP-P0-09 | 未配置时不自动除名 | planned |
| BR-03 | 三次缺勤/出勤率 50% 未确认：只提醒 + 人工审议 | WP-P0-16 | 低出勤提醒不改变资格 | planned |
| BR-04 | 集训队退出提前 15 个工作日校验，审批链可配置 | WP-P0-10 | 不足 15 日阻断提交并说明 | planned |
| BR-05 | 开学 2 周内完成计划，按业务日历计算 | WP-P0-14 | 截止日计算含节假日/调休 | planned |
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
| BR-21 | 分类被引用只能停用/合并；每题至少一主算法 | WP-P0-11/12 | 删除被引用分类失败 | planned |

## 3. 验收场景（14 个）

| AT | 场景 | 主要 FR | WP | E2E | 状态 |
|---|---|---|---|---|---|
| AT-01 | 竞赛类/综合管理类入会申请分别处理，学号不重复 | FR-02..04 | WP-P0-08 | e2e/at-01.spec.ts | planned |
| AT-02 | 会员+任职+队长，任期结束后权限调整、历史保留 | FR-01/03/19 | WP-P0-06 | e2e/at-02.spec.ts | planned |
| AT-03 | 多算法周任务 + 重复/待核实成绩；按三级下钻 | FR-06..12/37..44 | WP-P0-15 | e2e/at-03.spec.ts | planned |
| AT-04 | 新生培训+寒假任务；次数/时长/周数/反馈分别检查 | FR-15/18 | WP-P0-17 | e2e/at-04.spec.ts | planned |
| AT-05 | 第 4 名同期徒弟被阻断 | FR-16 | WP-P0-17 | e2e/at-05.spec.ts | planned |
| AT-06 | 未满 3 人只能草稿；补齐后确认；历史阵容不变 | FR-19/26 | WP-P0-19 | e2e/at-06.spec.ts | planned |
| AT-07 | 暑训报名与训练安排；年级/日历分别校验 | FR-21 | WP-P0-20 | e2e/at-07.spec.ts | planned |
| AT-08 | 校内通过但无外部回执：显示"待外部确认" | FR-24/25 | WP-P0-22 | e2e/at-08.spec.ts | planned |
| AT-09 | 名额分配不超额；排序依据与调整可追溯 | FR-27 | P1-排序 | e2e/at-09.spec.ts | deferred |
| AT-10 | 低出勤提醒不除名；普通/集训队退出分别处理 | FR-05 | WP-P0-10 | e2e/at-10.spec.ts | planned |
| AT-11 | 普通会员/队长/离任部长越权读取与下载被拒 | NFR-02/03 | WP-P0-26 | e2e/at-11.spec.ts（B0 服务端负向：AuthorizationTest / FileAccessTest） | in_progress |
| AT-12 | 考勤修订/成绩导入/导出/恢复备份；日志回执完整 | FR-12/30 | WP-P0-25 | e2e/at-12.spec.ts（B0 审计部分：AuditAndRuleTest） | in_progress |
| AT-13 | 三级目录+前置无环+导入重复/错误；每题一主算法 | FR-37..42 | WP-P0-11 | e2e/at-13.spec.ts | planned |
| AT-14 | 多维检索与算法覆盖统计，可下钻到有效完成 | FR-43/44 | P1-统计 | e2e/at-14.spec.ts | deferred |

## 4. 非功能需求（8 条）

| NFR | 要求 | 落地 | 状态 |
|---|---|---|---|
| NFR-01 | 桌面管理 + 手机报名/签到/任务/反馈 | Web 响应式 + P0-16 | planned |
| NFR-02 | 服务端按角色与数据范围校验；停用即时失效 | WP-P0-03/04/26；`JwtAuthenticationFilter`、`ScopeGuard`、`AuthService` | in_progress（B0 服务端已交付，E2E 待 WP-P0-26） |
| NFR-03 | 敏感信息按需展示、限制批量导出、密码不明文 | WP-P0-03/25；Argon2id、refresh 仅 HttpOnly、`FileService` 可见性 | in_progress（密码/令牌已满足，导出限制待 P0-25） |
| NFR-04 | 审批/状态/权限/成绩/考勤/导出可审计 | WP-P0-05；`AuditService` + PG V2 触发器 | in_progress（B0 规则/账号/附件已覆盖，业务域随 WP 接入） |
| NFR-05 | 一致性校验、导入预检、错误行与回执 | WP-P0-13 | planned |
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
## 6. B0 基础设施条款对照（2026-11-26）

| 条款 / 需求 | 落地位置 | 验证 | 状态 |
|---|---|---|---|
| DOM-01 学号唯一 | `db/migration/common/V1__core.sql`（`uq_person_student_no`） | 迁移成功；`SeedIdempotencyTest` | implemented |
| DOM-06 服务端鉴权 | `config/SecurityConfig.java`、各 Controller `@PreAuthorize`、`security/ScopeGuard.java` | `AuthorizationTest`、`RoutePermissionTest` | implemented |
| DOM-11 业务记录不物理删除 | V1 全部 FK `on delete restrict`；`file_object.archived_at`、`role_assignment.revoked_at`；无删除 API | 代码评审 + `FileAccessTest` 归档用例 | implemented |
| DOM-12 阈值配置化 | `rule_config` + `rule/RuleService.java` | `AuditAndRuleTest`、`SeedIdempotencyTest` | implemented |
| DOM-27 审计字段 | `audit/AuditLog.java`、`AuditService`（actor/时间/IP/对象/前后快照/原因/requestId） | `AuditAndRuleTest` | implemented |
| TECH-08 RBAC + 数据范围 | `role`、`role_permission`、`role_assignment`（SELF/TEAM/DEPARTMENT/GLOBAL） | `SeedIdempotencyTest`（11 角色）、`AuthorizationTest` | implemented（TEAM 判定待 B1 队伍模型） |
| TECH-09 权限码 `模块:动作` | `security/PermissionCode.java`、`packages/shared/src/permissions.ts` | `node scripts/check-permissions.mjs` → 64 个一致 | implemented |
| TECH-10 路由-权限元测试 | `src/test/.../security/RoutePermissionTest.java` | 24 测试全绿 | implemented |
| TECH-11 数据范围注入查询 | `security/ScopeGuard.java`、`audit/AuditService.java`（非 GLOBAL 只查本人） | `AuthorizationTest` | implemented（B0 域有限） |
| TECH-12 会话与即时失效 | `security/JwtService.java`、`auth/AuthService.java`、`auth/LoginAttemptService.java` | `AuthFlowTest` 7 例 | implemented |
| TECH-13 附件与签名下载 | `file/FileService.java`、`file/FileTokenService.java`、`file/FileController.java` | `FileAccessTest` 3 例 | implemented |
| DATA-05 / I-21 审计只增不改 | PG `V2__audit_append_only.sql` 触发器；无写改删端点 | `AuditAndRuleTest`（无 DELETE 路由）；触发器待 PG 实库验证 | implemented |
| I-23 / DENY-04 未确认阈值只提示 | 5 个 `MANUAL` 空值规则键；`RuleService` 拒绝 MANUAL 带值 | `AuditAndRuleTest`、`SeedIdempotencyTest` | implemented |
| DENY-11 / DENY-12 密钥与日志 | 密钥仅环境变量（prod 强制）、错误响应不含堆栈/凭据 | `GlobalExceptionHandler`；`application-prod.yml` | implemented |
