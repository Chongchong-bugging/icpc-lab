package cn.edu.scuec.icpc.lab.common;

/**
 * 统一错误响应（ADR-0001 §6）。
 * code 稳定、可被前端判断；message 面向用户；requestId 用于审计与排障。
 */
public record ApiError(String code, String message, String requestId) {
}