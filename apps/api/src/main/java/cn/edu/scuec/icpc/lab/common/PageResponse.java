package cn.edu.scuec.icpc.lab.common;

import java.util.List;

/**
 * 统一分页响应，列表接口禁止返回裸数组（便于后续扩展 total）。
 */
public record PageResponse<T>(List<T> items, int page, int size, long total) {
}