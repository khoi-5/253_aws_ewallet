package com.khoi.ewallet.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final JdbcTemplate jdbcTemplate;

    public JwtAuthenticationFilter(JwtService jwtService, JdbcTemplate jdbcTemplate) {
        this.jwtService = jwtService; this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null) { chain.doFilter(request, response); return; }
        if (!header.startsWith("Bearer ") || header.substring(7).isBlank()) {
            unauthorized(response, "UNAUTHORIZED", "Unauthorized"); return;
        }
        try {
            int accountId = jwtService.extractAccountId(header.substring(7));
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT id, role, status FROM users WHERE id = ? LIMIT 1", accountId);
            if (rows.isEmpty()) { unauthorized(response, "UNAUTHORIZED", "Unauthorized"); return; }
            Map<String, Object> row = rows.get(0);
            String role = String.valueOf(row.get("role"));
            String status = String.valueOf(row.get("status"));
            if ("blocked".equalsIgnoreCase(status)) {
                unauthorized(response, "ACCOUNT_BLOCKED", "Your account has been blocked by an administrator.", 403); return;
            }
            if (!"active".equalsIgnoreCase(status)) { unauthorized(response, "UNAUTHORIZED", "Unauthorized"); return; }
            AuthenticatedAccount principal = new AuthenticatedAccount(accountId, role, status);
            var authentication = new UsernamePasswordAuthenticationToken(
                    principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase())));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            chain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException exception) {
            SecurityContextHolder.clearContext(); unauthorized(response, "UNAUTHORIZED", "Unauthorized");
        }
    }

    private void unauthorized(HttpServletResponse response, String code, String message) throws IOException {
        unauthorized(response, code, message, 401);
    }
    private void unauthorized(HttpServletResponse response, String code, String message, int status) throws IOException {
        response.setStatus(status); response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }
}
