package in.pbl.ApiRateLimiter.service;

import in.pbl.ApiRateLimiter.dto.RateLimitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Core Rate Limiting Service implementing the Sliding Window Algorithm.
 * 
 * Demonstrates:
 * 1. Sliding Window Algorithm (Redis Sorted Set Log & Weighted Counter):
 *    - Eliminates the classic boundary bursting flaw of Fixed Window algorithms.
 * 2. Operating System Concept: Mutual Exclusion (Race Condition Prevention):
 *    - Redis executes the Lua script atomically on a single thread.
 *    - Check-then-act operations (ZREMRANGEBYSCORE -> ZCARD -> ZADD) happen in one atomic step.
 *    - No two concurrent requests can both read count = 9, pass the check, and exceed limit 10.
 */
@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);
    private static final String RATE_LIMIT_KEY_PREFIX = "rate_limit:user:";

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<List> slidingWindowLogScript;

    public RateLimiterService(StringRedisTemplate redisTemplate,
                              RedisScript<List> slidingWindowLogScript) {
        this.redisTemplate = redisTemplate;
        this.slidingWindowLogScript = slidingWindowLogScript;
    }

    /**
     * Checks rate limit using the Sliding Window Algorithm executed atomically in Redis via Lua.
     * 
     * @param userId User identifier
     * @param maxRequests Maximum allowed requests in window
     * @param windowSeconds Window duration in seconds
     * @return RateLimitResult containing status, remaining quota, and reset time
     */
    public RateLimitResult checkRateLimit(Long userId, int maxRequests, int windowSeconds) {
        long now = System.currentTimeMillis();
        long windowSizeMs = windowSeconds * 1000L;
        String redisKey = RATE_LIMIT_KEY_PREFIX + userId;
        String requestId = UUID.randomUUID().toString();

        try {
            // Execute atomic Lua script
            @SuppressWarnings("unchecked")
            List<Long> result = redisTemplate.execute(
                    slidingWindowLogScript,
                    Collections.singletonList(redisKey),
                    String.valueOf(now),
                    String.valueOf(windowSizeMs),
                    String.valueOf(maxRequests),
                    requestId
            );

            if (result != null && result.size() >= 3) {
                boolean allowed = result.get(0) == 1L;
                long remaining = result.get(1);
                long currentCount = result.get(2);
                long resetSeconds = windowSeconds; // Sliding window resets continuously

                if (allowed) {
                    return RateLimitResult.allowed(remaining, currentCount, maxRequests, resetSeconds);
                } else {
                    return RateLimitResult.exceeded(currentCount, maxRequests, resetSeconds);
                }
            }
        } catch (Exception e) {
            log.error("Error executing Redis rate limiter script for userId: {}", userId, e);
        }

        // Failsafe fallback: allow request if Redis temporarily unavailable
        return RateLimitResult.allowed(1, 1, maxRequests, windowSeconds);
    }

    /**
     * Alternative Sliding Window Counter (Weighted Window Approximation).
     * 
     * Formula:
     *   time_into_current_window = now - current_window_start
     *   prev_window_weight = (window_size - time_into_current_window) / window_size
     *   estimated_requests = (prev_window_count * prev_window_weight) + current_window_count
     */
    public double estimateSlidingWindowCounter(Long userId, int windowSeconds) {
        long now = System.currentTimeMillis();
        long windowSizeMs = windowSeconds * 1000L;
        long currentWindowStart = (now / windowSizeMs) * windowSizeMs;
        long prevWindowStart = currentWindowStart - windowSizeMs;

        String currKey = "rate_win:" + userId + ":" + currentWindowStart;
        String prevKey = "rate_win:" + userId + ":" + prevWindowStart;

        String currVal = redisTemplate.opsForValue().get(currKey);
        String prevVal = redisTemplate.opsForValue().get(prevKey);

        long currCount = currVal != null ? Long.parseLong(currVal) : 0;
        long prevCount = prevVal != null ? Long.parseLong(prevVal) : 0;

        double weight = (double) (windowSizeMs - (now - currentWindowStart)) / (double) windowSizeMs;
        return (prevCount * weight) + currCount;
    }
}
