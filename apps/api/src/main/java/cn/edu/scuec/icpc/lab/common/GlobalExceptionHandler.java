package cn.edu.scuec.icpc.lab.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 兜底异常处理：不向客户端泄露堆栈与内部细节，完整错误只进日志。
 * 业务异常在 P0 引入 BusinessException（带稳定 code）后单独处理。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handle(Exception ex, HttpServletRequest request) {
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        log.error("unhandled error requestId={} method={} path={}",
                requestId, request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("INTERNAL_ERROR", "服务器内部错误", requestId));
    }
}