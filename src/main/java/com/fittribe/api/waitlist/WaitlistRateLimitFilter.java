package com.fittribe.api.waitlist;

import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class WaitlistRateLimitFilter extends OncePerRequestFilter {

    private static final String WAITLIST_PATH = "/api/waitlist";

    private final WaitlistRateLimiter rateLimiter;

    public WaitlistRateLimitFilter(WaitlistRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only rate-limit POST /api/waitlist — skip everything else.
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !WAITLIST_PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String ip = extractClientIp(request);
        Bucket bucket = rateLimiter.resolveBucket(ip);

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write(
                "{\"data\":null,\"error\":{\"message\":\"Too many signups from this location. Try again in an hour.\",\"code\":\"RATE_LIMIT_EXCEEDED\"}}");
        }
    }

    // Railway sits behind a proxy; X-Forwarded-For carries the real client IP.
    // Take the first (leftmost) entry — that's the originating address.
    private String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
