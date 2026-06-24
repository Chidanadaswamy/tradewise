package com.seedling.platform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Token Bucket Pattern Rate Limiter for Finnhub API
 *
 * Purpose:
 * - Prevents exceeding Finnhub free tier limit (60 API calls/minute)
 * - Uses token bucket algorithm for smooth, predictable rate limiting
 * - Enables graceful degradation by signaling when to use cached data
 *
 * How it works:
 * - Tokens are generated at fixed rate: 60 tokens/minute = 1 token/second
 * - Each API call costs 1 token
 * - If tokens < 1, call is rate-limited and caller should use cached data
 * - Bucket capacity: 60 tokens (burst protection)
 *
 * Graceful Degradation Strategy:
 * 1. Request comes in → Check isAllowed()
 * 2. If isAllowed() = true → Make API call
 * 3. If isAllowed() = false → Serve cached/DB data instead
 * 4. User gets data either way, no errors, just possibly stale data
 *
 * Benefits:
 * - No request rejections or 429 errors to client
 * - Smooth experience even with rate limiting
 * - Prevents cascading API failures
 */
@Service
public class RateLimitService {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitService.class);

    // Finnhub Free Tier: 60 API calls / minute
    private static final double RATE_PER_SECOND = 1.0; // 60 calls/min = 1 call/sec
    private static final double MAX_TOKENS = 60.0;      // Burst capacity

    private static class TokenBucket {
        double tokens;
        long lastRefillTime;

        TokenBucket() {
            this.tokens = MAX_TOKENS;
            this.lastRefillTime = System.currentTimeMillis();
        }
    }

    // Track token buckets per API endpoint (quote, candle, etc.)
    private final Map<String, TokenBucket> buckets = new HashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * Check if API call is allowed based on rate limit
     *
     * Returns true if within rate limits, false if rate limited.
     * Caller should:
     * - If true: Make API call
     * - If false: Use cached data instead
     *
     * @param endpoint API endpoint name (e.g., "quote", "candle")
     * @return true if call is allowed, false if rate limited
     */
    public boolean isAllowed(String endpoint) {
        lock.writeLock().lock();
        try {
            TokenBucket bucket = buckets.computeIfAbsent(
                    endpoint,
                    k -> new TokenBucket()
            );

            // Refill tokens based on time elapsed
            long now = System.currentTimeMillis();
            long elapsedMs = now - bucket.lastRefillTime;
            double tokensToAdd = (elapsedMs / 1000.0) * RATE_PER_SECOND;

            bucket.tokens = Math.min(MAX_TOKENS, bucket.tokens + tokensToAdd);
            bucket.lastRefillTime = now;

            // Check if we have at least 1 token
            if (bucket.tokens >= 1.0) {
                bucket.tokens -= 1.0;
                logger.debug("Rate limit OK for {}: {:.2f} tokens remaining", endpoint, bucket.tokens);
                return true;
            } else {
                logger.warn("Rate limit exceeded for {}: {:.2f} tokens remaining", endpoint, bucket.tokens);
                return false;
            }

        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Get current token count for monitoring purposes
     * @param endpoint API endpoint name
     * @return Current token count
     */
    public double getTokenCount(String endpoint) {
        lock.readLock().lock();
        try {
            TokenBucket bucket = buckets.get(endpoint);
            if (bucket != null) {
                long now = System.currentTimeMillis();
                long elapsedMs = now - bucket.lastRefillTime;
                double tokensToAdd = (elapsedMs / 1000.0) * RATE_PER_SECOND;
                double currentTokens = Math.min(MAX_TOKENS, bucket.tokens + tokensToAdd);
                return currentTokens;
            }
            return MAX_TOKENS; // Not yet queried
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Reset rate limiter for testing purposes
     */
    public void reset() {
        lock.writeLock().lock();
        try {
            buckets.clear();
            logger.info("Rate limiter reset");
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Get rate limit status for all endpoints
     * @return Map of endpoint names to token counts
     */
    public Map<String, Double> getStatus() {
        lock.readLock().lock();
        try {
            Map<String, Double> status = new HashMap<>();
            long now = System.currentTimeMillis();

            for (Map.Entry<String, TokenBucket> entry : buckets.entrySet()) {
                TokenBucket bucket = entry.getValue();
                long elapsedMs = now - bucket.lastRefillTime;
                double tokensToAdd = (elapsedMs / 1000.0) * RATE_PER_SECOND;
                double currentTokens = Math.min(MAX_TOKENS, bucket.tokens + tokensToAdd);
                status.put(entry.getKey(), currentTokens);
            }
            return status;
        } finally {
            lock.readLock().unlock();
        }
    }
}

