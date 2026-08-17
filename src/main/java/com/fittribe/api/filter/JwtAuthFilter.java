package com.fittribe.api.filter;

import com.fittribe.api.repository.UserRepository;
import com.fittribe.api.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * JWT authentication filter — runs only within Spring Security filter chain.
 * NOT annotated with @Component to avoid double-registration as a servlet filter.
 * Registered via addFilterBefore in SecurityConfig instead.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService     jwtService;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService     = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/v1/auth/")
                || path.equals("/api/v1/health")
                // The public exercise list/detail need no auth, but
                // /api/v1/exercises/last-logged is per-user — let the JWT
                // filter run so the principal is populated for it.
                || (path.startsWith("/api/v1/exercises")
                        && !path.equals("/api/v1/exercises/last-logged"))
                // Admin analytics use query param auth (?key=secret), not JWT
                || path.startsWith("/api/admin/analytics/")
                // Admin jobs use X-Admin-Secret header, not JWT
                || path.startsWith("/api/v1/admin/jobs/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                UUID userId = jwtService.validateToken(token);
                // Reject tokens for soft-deleted / deactivated accounts
                boolean active = userRepository.findById(userId)
                        .map(u -> Boolean.TRUE.equals(u.getIsActive()))
                        .orElse(false);
                if (active) {
                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(userId, null, List.of());
                    SecurityContextHolder.getContext().setAuthentication(auth);
                } else {
                    SecurityContextHolder.clearContext();
                }
            } catch (Exception e) {
                // Invalid token — clear context, let Spring Security reject if endpoint needs auth
                SecurityContextHolder.clearContext();
            }
        }

        chain.doFilter(request, response);
    }
}
