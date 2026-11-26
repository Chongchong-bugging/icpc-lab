/** 角色编码镜像（与 ADR-0002 / 权限矩阵一致）。 */
export const ROLE_CODES = [
  "APPLICANT",
  "MEMBER",
  "TEAM_MEMBER",
  "CAPTAIN",
  "MENTOR",
  "MINISTER",
  "PRESIDENT",
  "COUNCIL",
  "ADVISOR",
  "OFFICE",
  "SYSADMIN",
] as const;

export type RoleCode = (typeof ROLE_CODES)[number];

/** 数据范围（ADR-0002 §3.6）。 */
export const SCOPE_TYPES = ["SELF", "TEAM", "DEPARTMENT", "GLOBAL"] as const;

export type ScopeType = (typeof SCOPE_TYPES)[number];