package in.pbl.ApiRateLimiter.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.pbl.ApiRateLimiter.dto.SessionData;
import in.pbl.ApiRateLimiter.entity.User;
import in.pbl.ApiRateLimiter.entity.UserSession;
import in.pbl.ApiRateLimiter.repository.UserRepository;
import in.pbl.ApiRateLimiter.repository.UserSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Session Management Service using Redis (In-Memory Cache) & MySQL (Persistent Storage).
 * 
 * Demonstrates:
 * - Operating System Concept: Memory Hierarchy & Cache-Aside Pattern
 *   1. L1/L2 Cache -> RAM (Redis, sub-millisecond) -> Disk (MySQL, millisecond).
 *   2. Session validation queries Redis first. On cache miss, queries MySQL and warms Redis.
 * - Database Concept: ACID Transactions (@Transactional) for session persistence.
 */
@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);
    private static final String SESSION_KEY_PREFIX = "session:";
    private static final long DEFAULT_SESSION_DURATION_MINUTES = 30;

    private final StringRedisTemplate redisTemplate;
    private final UserSessionRepository userSessionRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public SessionService(StringRedisTemplate redisTemplate,
                          UserSessionRepository userSessionRepository,
                          UserRepository userRepository,
                          ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.userSessionRepository = userSessionRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates a new session for a user.
     * Persists into MySQL and caches in Redis with TTL.
     */
    @Transactional
    public SessionData createSession(User user) {
        String token = UUID.randomUUID().toString().replace("-", "");
        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofMinutes(DEFAULT_SESSION_DURATION_MINUTES));

        // 1. Persist to MySQL (Database Durability)
        UserSession session = new UserSession(token, user, expiresAt);
        userSessionRepository.save(session);

        // 2. Prepare Session DTO
        SessionData sessionData = new SessionData(
                token,
                user.getId(),
                user.getUsername(),
                user.getApiKey(),
                user.getRateLimitTier().getName(),
                user.getRateLimitTier().getMaxRequests(),
                user.getRateLimitTier().getWindowSeconds(),
                user.getRateLimitTier().getMaxConcurrentRequests(),
                expiresAt.toEpochMilli()
        );

        // 3. Cache into Redis with TTL matching remaining expiration
        cacheSessionInRedis(token, sessionData, Duration.between(now, expiresAt));
        log.info("Created session for user: {} with token prefix: {}...", user.getUsername(), token.substring(0, 8));

        return sessionData;
    }

    /**
     * Validates a session token.
     * 1. Checks Redis cache (fast path).
     * 2. Checks MySQL (cold path) on cache miss.
     * 3. Rejects if session is expired or revoked.
     */
    @Transactional(readOnly = true)
    public Optional<SessionData> validateSession(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        String redisKey = SESSION_KEY_PREFIX + token;

        // Step 1: Redis Fast Path (RAM)
        try {
            String cachedJson = redisTemplate.opsForValue().get(redisKey);
            if (cachedJson != null) {
                SessionData sessionData = objectMapper.readValue(cachedJson, SessionData.class);
                if (!sessionData.isExpired()) {
                    return Optional.of(sessionData);
                } else {
                    // Evict expired session from Redis
                    redisTemplate.delete(redisKey);
                    return Optional.empty();
                }
            }
        } catch (Exception e) {
            log.warn("Redis read failed for token, falling back to MySQL: {}", e.getMessage());
        }

        // Step 2: MySQL Cold Path (Disk)
        Optional<UserSession> dbSessionOpt = userSessionRepository.findByToken(token);
        if (dbSessionOpt.isEmpty()) {
            return Optional.empty();
        }

        UserSession dbSession = dbSessionOpt.get();
        if (dbSession.isExpired() || !dbSession.getUser().isActive()) {
            return Optional.empty();
        }

        // Re-construct SessionData
        User user = dbSession.getUser();
        SessionData sessionData = new SessionData(
                token,
                user.getId(),
                user.getUsername(),
                user.getApiKey(),
                user.getRateLimitTier().getName(),
                user.getRateLimitTier().getMaxRequests(),
                user.getRateLimitTier().getWindowSeconds(),
                user.getRateLimitTier().getMaxConcurrentRequests(),
                dbSession.getExpiresAt().toEpochMilli()
        );

        // Warm up Redis Cache (Cache-Aside pattern)
        Duration remainingTtl = Duration.between(Instant.now(), dbSession.getExpiresAt());
        if (!remainingTtl.isNegative() && !remainingTtl.isZero()) {
            cacheSessionInRedis(token, sessionData, remainingTtl);
        }

        return Optional.of(sessionData);
    }

    /**
     * Invalidates a session (Logout).
     */
    @Transactional
    public void invalidateSession(String token) {
        if (token == null || token.isBlank()) {
            return;
        }

        // Remove from Redis RAM
        redisTemplate.delete(SESSION_KEY_PREFIX + token);

        // Mark revoked in MySQL
        userSessionRepository.findByToken(token).ifPresent(session -> {
            session.setRevoked(true);
            userSessionRepository.save(session);
        });
    }

    private void cacheSessionInRedis(String token, SessionData data, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(data);
            redisTemplate.opsForValue().set(SESSION_KEY_PREFIX + token, json, ttl);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize session to JSON", e);
        }
    }
}
