package com.ram.ratelimiter.controller;

import com.ram.ratelimiter.dto.RateLimitResult;
import com.ram.ratelimiter.service.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RateLimiterController {

    private final RateLimiterService rateLimiterService;

    /**
     * GET /api/check/user
     * Rate limit by userId using Token Bucket.
     * Simulates a protected API endpoint.
     */
    @GetMapping("/check/user")
    public ResponseEntity<RateLimitResult> checkByUser(
            @AuthenticationPrincipal UserDetails userDetails) {

        String userId = userDetails.getUsername();
        RateLimitResult result = rateLimiterService.checkByUser(userId);

        if (!result.isAllowed()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(result);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/check/ip
     * Rate limit by IP using Sliding Window.
     * Works even without login.
     */
    @GetMapping("/check/ip")
    public ResponseEntity<RateLimitResult> checkByIp(HttpServletRequest request) {
        String ip = getClientIp(request);
        RateLimitResult result = rateLimiterService.checkByIp(ip);

        if (!result.isAllowed()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(result);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/check/both
     * Applies both algorithms - user (token bucket) + IP (sliding window).
     * Both must pass for request to go through.
     */
    @GetMapping("/check/both")
    public ResponseEntity<RateLimitResult> checkBoth(
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest request) {

        String userId = userDetails.getUsername();
        String ip     = getClientIp(request);

        RateLimitResult result = rateLimiterService.checkBoth(userId, ip);

        if (!result.isAllowed()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(result);
        }
        return ResponseEntity.ok(result);
    }

    // handles both direct requests and requests behind a proxy/load balancer
    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
