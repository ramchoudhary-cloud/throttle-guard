package com.ram.ratelimiter.algorithm;

import com.ram.ratelimiter.dto.RateLimitResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Token Bucket Algorithm
 *
 * Idea is simple: imagine a bucket that holds tokens.
 * Each request costs 1 token. Tokens refill over time.
 * If bucket is empty, request is rejected.
 *
 * Good for: handling burst traffic gracefully.
 * If user has saved up tokens, they can send a burst of requests.
 *
 * Redis keys used:
 * - rate:token:{key}:tokens  -> current token count
 * - rate:token:{key}:last    -> last refill timestamp
 */
@Slf4j
@Component
public class TokenBucketAlgorithm {

    private final StringRedisTemplate redis;

    @Value("${rate.limit.token-bucket.capacity}")
    private int capacity;

    @Value("${rate.limit.token-bucket.refill-per-minute}")
    private int refillPerMinute;

    public TokenBucketAlgorithm(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public RateLimitResult isAllowed(String key) {
        String tokenKey = "rate:token:" + key + ":tokens";
        String lastRefillKey = "rate:token:" + key + ":last";

        long now = Instant.now().getEpochSecond();

        // get current tokens - default to full bucket for new users
        String tokenVal = redis.opsForValue().get(tokenKey);
        String lastVal  = redis.opsForValue().get(lastRefillKey);

        long tokens    = tokenVal != null ? Long.parseLong(tokenVal) : capacity;
        long lastRefill = lastVal != null ? Long.parseLong(lastVal)  : now;

        // calculate how many tokens to add based on time passed
        long secondsPassed = now - lastRefill;
        long tokensToAdd   = (secondsPassed * refillPerMinute) / 60;

        if (tokensToAdd > 0) {
            tokens = Math.min(capacity, tokens + tokensToAdd);
            // update last refill time only if we actually added tokens
            redis.opsForValue().set(lastRefillKey, String.valueOf(now), 10, TimeUnit.MINUTES);
        }

        if (tokens <= 0) {
            redis.opsForValue().set(tokenKey, "0", 10, TimeUnit.MINUTES);
            log.debug("Token bucket BLOCKED for key={}, tokens=0", key);
            return new RateLimitResult(false, 0, "Rate limit exceeded. Try again later.");
        }

        // consume one token
        tokens--;
        redis.opsForValue().set(tokenKey, String.valueOf(tokens), 10, TimeUnit.MINUTES);
        log.debug("Token bucket ALLOWED for key={}, remaining={}", key, tokens);
        return new RateLimitResult(true, tokens, "Request allowed.");
    }
}
