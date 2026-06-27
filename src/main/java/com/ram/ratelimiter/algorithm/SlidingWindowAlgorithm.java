package com.ram.ratelimiter.algorithm;

import com.ram.ratelimiter.dto.RateLimitResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Sliding Window Counter Algorithm
 *
 * Instead of a fixed window (e.g. 0-60s, 60-120s), this tracks
 * requests in a rolling window. Smoother and fairer than fixed window.
 *
 * How it works:
 * - We divide the window into smaller buckets (one per second)
 * - Each request increments the current second's bucket
 * - To check limit: sum up all buckets within the last N seconds
 * - Old buckets expire automatically via Redis TTL
 *
 * Example: window=60s, max=20 requests
 * - If user made 19 requests in last 60 seconds, they can make 1 more
 * - If at 20, next request is blocked until oldest request falls out of window
 *
 * Redis key: rate:sliding:{key}:{second}
 */
@Slf4j
@Component
public class SlidingWindowAlgorithm {

    private final StringRedisTemplate redis;

    @Value("${rate.limit.sliding-window.max-requests}")
    private int maxRequests;

    @Value("${rate.limit.sliding-window.window-seconds}")
    private int windowSeconds;

    public SlidingWindowAlgorithm(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public RateLimitResult isAllowed(String key) {
        long now = Instant.now().getEpochSecond();
        long windowStart = now - windowSeconds;

        // count requests in the current window
        long totalRequests = 0;
        for (long second = windowStart; second <= now; second++) {
            String bucketKey = "rate:sliding:" + key + ":" + second;
            String val = redis.opsForValue().get(bucketKey);
            if (val != null) {
                totalRequests += Long.parseLong(val);
            }
        }

        if (totalRequests >= maxRequests) {
            long remaining = 0;
            log.debug("Sliding window BLOCKED for key={}, total={}", key, totalRequests);
            return new RateLimitResult(false, remaining,
                    "Rate limit exceeded. Too many requests in the last " + windowSeconds + " seconds.");
        }

        // increment current second's bucket
        String currentBucket = "rate:sliding:" + key + ":" + now;
        redis.opsForValue().increment(currentBucket);
        // expire after window passes - no need to keep old data
        redis.expire(currentBucket, windowSeconds + 1, TimeUnit.SECONDS);

        long remaining = maxRequests - totalRequests - 1;
        log.debug("Sliding window ALLOWED for key={}, remaining={}", key, remaining);
        return new RateLimitResult(true, remaining, "Request allowed.");
    }
}
