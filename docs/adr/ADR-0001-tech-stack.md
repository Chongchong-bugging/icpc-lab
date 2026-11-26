# ADR-0001 技术栈：Java 21 + Spring Boot 3.5 模块化单体

- **状态**：Accepted（2026-11-26）
- **决策人**：项目负责人
- **取代**：ICPC-LAB-GOV-001 v1.0.0 的 TypeScript 全栈基线
- **关联**：GOV-001 v1.1.0 第 4 章 TECH-01 至 TECH-20；GOV-01、CHG-02；G0 门禁 H1

---

## 1. 背景

课程与部署环境要求后端使用 **Java / Spring Boot**。原 v1.0.0 基线为 React + NestJS + PostgreSQL，前后端同语言，便于共享类型；改用 Java 后，类型共享链路断开，必须用契约（OpenAPI）与测试重新建立前后端一致性保障。

本项目是**单机部署的模块化单体**：一个 API 服务、一个 React SPA、一个 PostgreSQL、一个对象存储。业务覆盖 44 条 FR、11 类角色、7 个审批/业务状态机，历史快照与审计是硬约束。团队规模小，不允许引入微服务、消息队列、K8s。

本机环境：JDK 25.0.1、Maven 3.9.16、Node 24.12.0、Docker 29.8.0；`.m2` 与 npm 仓库可访问。

## 2. 决策

### 2.1 语言与构建

| 项 | 决策 | 理由 |
|---|---|---|
| 语言级别 | **Java 21 LTS**（`maven.compiler.release=21`） | Spring Boot 3.5 支持区间内；本机 JDK 25 以 `--release 21` 编译，产物可在 Java 21 运行时部署 |
| 构建 | **Maven 3.9**，单模块 `apps/api` | 课程/团队熟悉度最高；单模块避免多模块聚合的额外复杂度，模块边界用包 + ArchUnit 守护 |
| 框架 | **Spring Boot 3.5.15**（锁定小版本） | 本机 `.m2` 已有 3.5.15；补丁升级只改 parent 版本并跑全量测试 |
| Web | **Spring MVC**（阻塞式） | 业务以事务型 CRUD/审批为主，无需响应式复杂度；团队更易维护 |
| 持久化 | **Spring Data JPA + Hibernate**，复杂报表用 `JdbcTemplate`/原生 SQL | 领域关系多、状态机多，JPA 聚合映射收益大；统计查询不强行 ORM |
| 迁移 | **Flyway**，`spring.jpa.hibernate.ddl-auto=validate` | 结构变更只走迁移；禁止 Hibernate 自动建表 |
| 校验 | **Bean Validation**（`jakarta.validation`） | 后端为权威校验；前端仅做体验 |
| 安全 | **Spring Security 6** + JWT（jjwt 0.12）+ Argon2id | 见 4.4；方法级 `@PreAuthorize` + 数据范围服务 |
| 文档 | **springdoc-openapi 2.8.x** | 从代码生成 OpenAPI 3.1，供前端生成类型与契约测试 |
| 测试 | JUnit 5 + MockMvc + **Testcontainers**（PostgreSQL）+ ArchUnit | 集成测试不 mock 权限层（ENG-07） |
| 部署 | Docker Compose + Nginx，单机 | 与 TECH-15/16/17 一致 |

### 2.2 前端（保持不变）

React 19 + Vite 7 + TypeScript 5（strict）+ React Router 7 + TanStack Query 5 + Ant Design 5。ECharts 在 P1 能力报告时引入。包管理统一用 pnpm workspace。

### 2.3 代码组织

Maven 单模块，按业务域分包；**包即模块**：

```text
cn.edu.scuec.icpc.lab
├── IcpcLabApplication.java
├── common/          # 错误响应、分页、审计切面、安全基础设施、时钟、ID
├── identity/        # 账号、认证、角色、权限点、角色分配
├── organization/    # 部门、岗位、届次、任职
├── member/          # 人员、会员、申请、考核、学期注册、退出
├── problem/         # 算法目录、题目、标签、导入
├── training/        # 计划、课次、任务、考勤、完成记录、新生/寒暑训
├── contest/         # 队伍、赛事、报名、成绩、选拔
├── approval/        # 通用审批实例与步骤
├── notification/    # 站内通知
├── report/          # 统计口径与导出
└── system/          # 字典、规则配置、业务日历、文件、后台任务
```

规则：

1. 跨模块只调用对方的 **application service**，不得注入对方的 repository，不得跨模块写表（TECH-01）。
2. 模块依赖方向：`common` 被所有模块依赖；业务模块之间允许的依赖在 P0-01 用 ArchUnit 固化，禁止环。
3. Controller 只做协议转换与权限声明；业务规则在 application service；事务边界在 service。
4. DTO 用 `record`；JPA 实体不用 `record`。允许 Lombok 的 `@Getter`/`@RequiredArgsConstructor`，**禁止** `@Data`/`@EqualsAndHashCode` 用在实体上。
5. 所有时间用 `Instant`/`OffsetDateTime`（UTC 存储），注入 `Clock` 便于测试；金额用 `BigDecimal`，DB `numeric(12,2)`。
6. 主键用应用侧生成的 `UUID`（v4），DB 类型 `uuid`；对外另给稳定业务编号（如会员号），不以自增 ID 暴露。

### 2.4 API 与错误

- 前缀 `/api/v1`，资源名词复数（ENG-01）。
- 错误响应固定为 `{ code, message, details?, requestId }`（ENG-02），HTTP 状态语义正确，不用 200 包失败。
- 分页 `{ items, page, pageSize, total }`，`pageSize <= 100`（ENG-03、DATA-09）。
- 写接口支持 `Idempotency-Key`；更新接口带 `version` 乐观锁，冲突 409（ENG-04）。
- `requestId` 由过滤器生成/透传，写入 MDC、错误响应、审计（ENG-05、ENG-11）。

### 2.5 认证与授权

- 登录：用户名/学号 + 密码（Argon2id），失败计数与锁定，限流按 IP + 账号。
- 访问令牌 JWT 15 分钟，只放内存；刷新令牌随机 256 位，HttpOnly + Secure + SameSite=Lax Cookie，服务端存哈希并轮换。
- 账号停用、角色变更、密码重置通过 `token_version` 使旧令牌立即失效（TECH-12）。
- 权限点权威定义在后端 `PermissionCode` 枚举；前端 `packages/shared` 为镜像，CI 比对（TECH-09）。
- 数据范围（本人/队伍/部门/全局）在 service/repository 查询条件统一注入，禁止只在列表接口过滤（TECH-11）。
- 业务审批与系统管理权限分离：`ADMIN` 默认不获得业务审批权（2.2 权限矩阵）。

### 2.6 数据库

- PostgreSQL 16，`utf8`，时区 UTC；所有表含 `created_at`、`updated_at`、`version`。
- Flyway 迁移命名 `V<yyyyMMddHHmm>__<snake_case>.sql`；只前进，破坏性变更用"新列 + 回填 + 切换 + 删除"多步迁移。
- 参考数据（角色、权限、算法大类、难度、赛事类型）用版本化迁移写入；业务规则进 `rule_config`，不硬编码。
- 唯一性/外键/检查约束尽量下沉 DB；跨行约束（如师傅带徒 ≤3）在 service 校验 + 定时对账，DB 只做辅助约束。
- 测试用 Testcontainers 启动真实 PostgreSQL，迁移在空库重放（ENG-10、CI 门禁 5）。

## 3. 被否方案

| 方案 | 否决原因 |
|---|---|
| 继续 NestJS/TypeScript | 课程要求 Java/Spring Boot，无法满足交付约束 |
| Spring WebFlux | 业务以事务与阻塞查询为主，响应式增加团队负担，收益低 |
| 多模块 Maven 聚合 | 当前规模下增加构建与版本协调成本；包边界 + ArchUnit 足够 |
| MyBatis 作为唯一持久化 | 领域对象与状态机关系复杂，JPA 聚合映射更合适；报表仍可用 SQL |
| Hibernate `ddl-auto=update` | 不可审计、不可重放，违反迁移只前进与历史可追溯 |
| 会话 Cookie 认证 | SPA + 未来移动端需要无状态访问令牌；刷新 Cookie 已覆盖安全性 |
| 引入 Redis/MQ | 单机部署、任务量小；数据库任务表 + 进程内调度足够（TECH-03） |

## 4. 后果与风险

**正面**：满足课程约束；Spring 生态成熟；JPA + Flyway 适合复杂领域与审计；单模块部署简单。

**风险与缓解**：

1. **前后端类型漂移**：后端 DTO 与前端类型各写一份。缓解：springdoc 导出 OpenAPI，P1 生成前端客户端；契约快照进 CI（TECH-20）。
2. **JPA N+1 与懒加载泄漏**：缓解：DTO 投影、`@EntityGraph`/fetch join、`hibernate.generate_statistics` 测试断言查询数；禁止把实体直接序列化出 Controller。
3. **Java 样板代码**：缓解：record DTO、Lombok 受限使用、MapStruct 暂不引入（手写映射集中在 `*Mapper`）。
4. **Argon2 依赖**：需要 BouncyCastle；在 P0-03 锁定版本并写登录性能测试。
5. **单机容量**：Q-13 未定；按数百人、低并发设计，压测目标在 G1 前确认。
6. **包边界退化**：P0-01 加 ArchUnit 规则，CI 失败即阻断。

## 5. 验证方式

- `mvn -f apps/api/pom.xml verify`：编译（release 21）、单元/MockMvc 测试、打包。
- `mvn -f apps/api/pom.xml test` 在 Testcontainers 可用时跑集成测试（P0-02 起）。
- `node scripts/check-permissions.mjs`：权限码两端一致。
- CI：Java 21 + Node 22/24 两条流水线；缓存 Maven 与 pnpm。
- G0 出口：`GET /api/v1/system/info` 与 `/actuator/health` 可访问；Web 能显示 API 状态；CI 全绿。

## 6. 合规映射

| 条款 | 落实 |
|---|---|
| TECH-01/02/03 | 单模块单体、无 MQ/K8s、DB 任务表 |
| TECH-05 | 无 raw type；空 catch 禁止；前端 strict |
| TECH-07 | web 不引 api；shared 无框架运行时；DB 访问仅在 api |
| TECH-09/10 | `PermissionCode` 枚举 + `@PreAuthorize` + 路由权限元测试 |
| TECH-12 | JWT + 刷新 Cookie + `token_version` |
| TECH-15/16/17 | 环境变量、备份、可回滚迁移 |
| TECH-20 | springdoc 契约导出与差异检查 |