package cn.edu.scuec.icpc.lab.security;

import java.util.UUID;

/**
 * 一次命中的授权：权限码来自角色，范围来自 role_assignment（并集语义）。
 */
public record ScopeGrant(String roleCode, String permission, ScopeType scopeType, UUID scopeId) {
}