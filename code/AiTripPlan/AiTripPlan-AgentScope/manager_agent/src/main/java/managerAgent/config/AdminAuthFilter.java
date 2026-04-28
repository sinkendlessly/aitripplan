package managerAgent.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Base64;

/**
 * 简易 Basic Auth 过滤器，用于保护 /admin/ 监控端点
 * 凭据通过 ADMIN_USER / ADMIN_PASSWORD 环境变量配置
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "admin.auth.enabled", havingValue = "true", matchIfMissing = true)
public class AdminAuthFilter implements Filter {

    private final String adminUser;
    private final String adminPassword;

    public AdminAuthFilter() {
        this.adminUser = System.getenv().getOrDefault("ADMIN_USER", "admin");
        this.adminPassword = System.getenv().getOrDefault("ADMIN_PASSWORD", "admin123");
        log.info("Admin 监控鉴已启用 (user: {})", adminUser);
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
                         FilterChain chain) throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String path = request.getRequestURI();
        // 只保护 /admin/ 路径
        if (!path.startsWith("/admin/")) {
            chain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            response.setHeader("WWW-Authenticate", "Basic realm=\"ATPlan Admin\"");
            response.sendError(401, "需要认证");
            return;
        }

        String base64 = authHeader.substring(6);
        String decoded = new String(Base64.getDecoder().decode(base64));
        String[] parts = decoded.split(":", 2);

        if (parts.length == 2 && adminUser.equals(parts[0]) && adminPassword.equals(parts[1])) {
            chain.doFilter(request, response);
        } else {
            response.sendError(403, "认证失败");
        }
    }
}
