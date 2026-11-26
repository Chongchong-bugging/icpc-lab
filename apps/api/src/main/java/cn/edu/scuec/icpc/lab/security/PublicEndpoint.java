package cn.edu.scuec.icpc.lab.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记无需认证的端点。TECH-10 路由权限元测试要求：
 * 每个控制器方法必须要么带 @PublicEndpoint，要么带 @PreAuthorize 且权限码存在于 PermissionCode。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface PublicEndpoint {
}