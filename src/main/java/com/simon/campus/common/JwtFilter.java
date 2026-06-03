package com.simon.campus.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 【导读】JWT 过滤器：每个 HTTP 请求进入 Controller 前执行。
 * <ul>
 *   <li>从 Header {@code Authorization: Bearer xxx} 或 query {@code ?token=xxx} 取 token（SSE 用 query）</li>
 *   <li>解析后写入 SecurityContext：principal=username，credentials=userId，authority=ROLE_xxx</li>
 *   <li>后端用 {@code auth.getCredentials()} 取当前用户 ID；{@code @PreAuthorize} 校验角色</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String token = extractToken(request);
        if (token != null) {
            try {
                Long userId   = jwtUtil.getUserId(token);
                String username = jwtUtil.getUsername(token);
                String role     = jwtUtil.getRole(token);

                if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    var auth = new UsernamePasswordAuthenticationToken(
                        username, userId,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))
                    );
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (Exception ignored) {
                // Invalid/expired token — continue unauthenticated
            }
        }

        chain.doFilter(request, response);
    }

    /** Extract JWT from Authorization header or 'token' query param (for SSE EventSource). */
    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        String param = request.getParameter("token");
        if (param != null && !param.isBlank()) {
            return param;
        }
        return null;
    }
}
