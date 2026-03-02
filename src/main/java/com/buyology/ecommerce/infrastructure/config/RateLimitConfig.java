package com.buyology.ecommerce.infrastructure.config;

import org.springframework.context.annotation.Configuration;

// Redis + Bucket4j wiring is handled inside RateLimitingFilter (lazy init on first request).
@Configuration
public class RateLimitConfig {
}
