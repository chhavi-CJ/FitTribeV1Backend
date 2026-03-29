package com.fittribe.api.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed-window in-memory rate limiter for POST /auth/verify-firebase.
 * Limit: 10 requests per 60-second window per IP address.
 *
 * ConcurrentHashMap.compute() is atomic per key, so no explicit locking needed.
 * State is process-local — sufficient for single-node Railway deployment.
 */
@Component
public class AuthRateLimiter {

    private static final int  MAX_REQUESTS  = 10;
    private static final long WINDOW_MILLIS = 60_000L;

    // IP → [windowStartMs, requestCount]
    private final ConcurrentHashMap<String, long[]> buckets = new ConcurrentHashMap<>();

    /**
     * Returns true if the request is within the limit and should be allowed.
     * Returns false if the IP has exceeded 10 requests in the current 60-second window.
     */
    public boolean tryConsume(String ip) {
        long now = System.currentTimeMillis();
        long[] bucket = buckets.compute(ip, (k, existing) -> {
            if (existing == null || now - existing[0] >= WINDOW_MILLIS) {
                // New window
                return new long[]{now, 1};
            }
            existing[1]++;
            return existing;
        });
        return bucket[1] <= MAX_REQUESTS;
    }
}
