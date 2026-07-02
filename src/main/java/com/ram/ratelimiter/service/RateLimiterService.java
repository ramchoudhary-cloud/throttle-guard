package com.ram.ratelimiter.service;

import com.ram.ratelimiter.algorithm.SlidingWindowAlgorithm;
import com.ram.ratelimiter.algorithm.TokenBucketAlgorithm;
import com.ram.ratelimiter.dto.RateLimitResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Main service that applies rate limiting.
 * Supports two strategies - token bucket and sliding window.
 *
 * Both can be applied simultaneously.
 * Request is only allowed if BOTH pass.
 *
 * Key format:
 * - By user:  "user:{userId}"
 * - By IP:    "ip:{ipAddress}"
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private final TokenBucketAlgorithm tokenBucket;
    private final SlidingWindowAlgorithm slidingWindow;

    /**
     * Check rate limit by userId using token bucket.
     * Used when we know who the user is (authenticated requests).
     */
    public RateLimitResult checkByUser(String userId) {
        String key = "user:" + userId;
        log.debug("Checking token bucket for userId={}", userId);
        return tokenBucket.isAllowed(key);
    }

    /**
     * Check rate limit by IP using sliding window.
     * Used for all requests including unauthenticated ones.
     */
    public RateLimitResult checkByIp(String ipAddress) {
        String key = "ip:" + ipAddress;
        log.debug("Checking sliding window for ip={}", ipAddress);
        return slidingWindow.isAllowed(key);
    }

    /**
     * Check both - by user AND by IP.
     * Both must pass for the request to go through.
     * This gives us double protection.
     */
    public RateLimitResult checkBoth(String userId, String ipAddress) {
        RateLimitResult userResult = checkByUser(userId);
        if (!userResult.isAllowed()) {
            return userResult;  // blocked at user level, no need to check IP
        }

        RateLimitResult ipResult = checkByIp(ipAddress);
        if (!ipResult.isAllowed()) {
            return ipResult;    // blocked at IP level
        }

        return userResult;  // both passed, return user result with remaining count
    }
}
