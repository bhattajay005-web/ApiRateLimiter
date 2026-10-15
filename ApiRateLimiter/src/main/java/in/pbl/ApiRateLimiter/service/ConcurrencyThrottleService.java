package in.pbl.ApiRateLimiter.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * Service to track and throttle concurrent requests arriving at the exact same time.
 * 
 * Demonstrates Operating System Concepts:
 * 1. Mutual Exclusion & Bulkhead Pattern:
 *    Prevents a single user from sending hundreds of simultaneous requests that exhaust
 *    all Tomcat worker threads or database connection pool sockets.
 * 2. Semaphores:
 *    Controls the number of simultaneous permits granted to a user's active in-flight requests.
 * 3. Redis Distributed In-Flight Counter:
 *    Ensures concurrency limits are enforced across multiple clustered application instances.
 */
@Service
public class ConcurrencyThrottleService {

    private static final Logger log = LoggerFactory.getLogger(ConcurrencyThrottleService.class);
    private static final String CONCURRENT_KEY_PREFIX = "concurrent_req:";
    private static final Duration CONCURRENT_KEY_SAFETY_TTL = Duration.ofSeconds(30);

    private final StringRedisTemplate redisTemplate;
    // Local in-memory semaphores per user (OS Semaphore concept)
    private final ConcurrentHashMap<Long, Semaphore> localSemaphores = new ConcurrentHashMap<>();

    public ConcurrencyThrottleService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Attempts to acquire a concurrency permit for simultaneous requests.
     * 
     * @param userId user identifier
     * @param maxConcurrent allowed simultaneous in-flight requests
     * @return true if permit acquired, false if concurrency limit exceeded
     */
    public boolean tryAcquire(Long userId, int maxConcurrent) {
        String key = CONCURRENT_KEY_PREFIX + userId;

        // 1. Redis Atomic Increment (Distributed Concurrency Tracking)
        Long currentInFlight = redisTemplate.opsForValue().increment(key);
        if (currentInFlight != null && currentInFlight == 1) {
            // First in-flight request: set safety TTL so counter doesn't leak if app crashes
            redisTemplate.expire(key, CONCURRENT_KEY_SAFETY_TTL);
        }

        if (currentInFlight != null && currentInFlight > maxConcurrent) {
            // Exceeded allowed simultaneous requests: decrement and deny
            redisTemplate.opsForValue().decrement(key);
            log.warn("Concurrency limit exceeded for userId: {} (Active: {}, Max: {})",
                    userId, currentInFlight, maxConcurrent);
            return false;
        }

        // 2. OS Local Semaphore Permit Acquisition
        Semaphore semaphore = localSemaphores.computeIfAbsent(userId, k -> new Semaphore(maxConcurrent, true));
        boolean localAcquired = semaphore.tryAcquire();
        if (!localAcquired) {
            // Rollback Redis count if local semaphore is saturated
            redisTemplate.opsForValue().decrement(key);
            return false;
        }

        return true;
    }

    /**
     * Releases the concurrency permit once the request finishes execution.
     */
    public void release(Long userId) {
        String key = CONCURRENT_KEY_PREFIX + userId;
        try {
            Long remaining = redisTemplate.opsForValue().decrement(key);
            if (remaining != null && remaining <= 0) {
                redisTemplate.delete(key);
            }
        } catch (Exception e) {
            log.error("Error decrementing Redis concurrency counter for userId: {}", userId, e);
        }

        Semaphore semaphore = localSemaphores.get(userId);
        if (semaphore != null) {
            semaphore.release();
        }
    }

    /**
     * Inspects currently active simultaneous requests for a user.
     */
    public long getCurrentInFlight(Long userId) {
        String key = CONCURRENT_KEY_PREFIX + userId;
        String val = redisTemplate.opsForValue().get(key);
        return val != null ? Long.parseLong(val) : 0;
    }
}
