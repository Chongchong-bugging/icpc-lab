package cn.edu.scuec.icpc.lab.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import cn.edu.scuec.icpc.lab.common.ApiError;
import cn.edu.scuec.icpc.lab.support.ApiTestSupport;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TECH-10 路由-权限元测试：每个业务路由必须显式声明权限点或标记为公开端点，
 * 且声明的权限点必须存在于 PermissionCode（矩阵漂移即失败）。
 */
class RoutePermissionTest extends ApiTestSupport {

    private static final Pattern AUTHORITY = Pattern.compile("hasAuthority\\('([^']+)'\\)");

    @Autowired
    private ApplicationContext context;

    @Test
    void everyRouteIsGuardedOrExplicitlyPublic() {
        List<String> problems = new ArrayList<>();
        int guarded = 0;
        int publicRoutes = 0;

        for (Object bean : context.getBeansWithAnnotation(RestController.class).values()) {
            Class<?> type = AopUtils.getTargetClass(bean);
            boolean classPublic = AnnotatedElementUtils.hasAnnotation(type, PublicEndpoint.class);
            for (Method method : type.getDeclaredMethods()) {
                if (!AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)) {
                    continue;
                }
                String route = type.getSimpleName() + "#" + method.getName();
                boolean methodPublic = AnnotatedElementUtils.hasAnnotation(method, PublicEndpoint.class);
                if (classPublic || methodPublic) {
                    publicRoutes++;
                    continue;
                }
                PreAuthorize preAuthorize = AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class);
                if (preAuthorize == null) {
                    problems.add("路由缺少权限声明：" + route);
                    continue;
                }
                Matcher matcher = AUTHORITY.matcher(preAuthorize.value());
                if (!matcher.find()) {
                    problems.add("无法解析权限点：" + route + " -> " + preAuthorize.value());
                    continue;
                }
                String code = matcher.group(1);
                if (PermissionCode.fromCode(code).isEmpty()) {
                    problems.add("权限点未在枚举中声明：" + route + " -> " + code);
                } else {
                    guarded++;
                }
            }
        }

        if (!problems.isEmpty()) {
            fail("路由鉴权元测试失败：\n" + String.join("\n", problems));
        }
        assertThat(guarded).isGreaterThanOrEqualTo(12);
        assertThat(publicRoutes).isGreaterThanOrEqualTo(5);
    }

    @Test
    void apiErrorShapeIsStable() {
        ApiError error = new ApiError("FORBIDDEN", "无权访问", "req-12345678");
        assertThat(error.code()).isEqualTo("FORBIDDEN");
        assertThat(error.requestId()).isEqualTo("req-12345678");
    }
}