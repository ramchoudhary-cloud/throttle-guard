package com.ram.ratelimiter.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RateLimitResult {
    private boolean allowed;
    private long remainingTokens;  // how many requests left
    private String message;
}
