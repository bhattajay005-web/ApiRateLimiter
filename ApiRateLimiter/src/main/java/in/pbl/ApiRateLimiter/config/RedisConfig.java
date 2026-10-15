package in.pbl.ApiRateLimiter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.List;

/**
 * Redis Configuration for Rate Limiting & Session Management.
 * 
 * Demonstrates Operating System Concepts:
 * - Mutual Exclusion / Critical Section Solution:
 *   Redis executes Lua scripts atomically. This prevents race conditions
 *   when multiple requests from the same user arrive at the exact same millisecond.
 * - In-Memory Fast Path vs Disk Storage (Memory Hierarchy):
 *   Redis RAM latency (~0.5ms) vs MySQL disk write latency (~20ms).
 */
@Configuration
public class RedisConfig {

    @Bean
    public com.fasterxml.jackson.databind.ObjectMapper objectMapper() {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        var jsonSerializer = org.springframework.data.redis.serializer.RedisSerializer.json();

        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    /**
     * Atomic Lua script implementing the Sliding Window Algorithm.
     * 
     * Input Keys:
     *   KEYS[1] = rate_limit:{userId} (Redis Sorted Set)
     * Input Arguments:
     *   ARGV[1] = now (Current timestamp in milliseconds)
     *   ARGV[2] = windowSizeMs (Window duration in milliseconds)
     *   ARGV[3] = maxRequests (Allowed limit in window)
     *   ARGV[4] = requestId (Unique request identifier)
     * 
     * Returns a List of Long:
     *   [0] -> 1 (Allowed) or 0 (Blocked / Limit Exceeded)
     *   [1] -> Remaining request count in current window
     *   [2] -> Total active requests in current window (after this request if allowed)
     */
    @Bean
    public RedisScript<List> slidingWindowLogScript() {
        String script = 
            "local key = KEYS[1]\n" +
            "local now = tonumber(ARGV[1])\n" +
            "local windowSizeMs = tonumber(ARGV[2])\n" +
            "local maxRequests = tonumber(ARGV[3])\n" +
            "local requestId = ARGV[4]\n" +
            "local clearBefore = now - windowSizeMs\n" +
            "\n" +
            "-- 1. Evict timestamps that fall outside the sliding window\n" +
            "redis.call('ZREMRANGEBYSCORE', key, 0, clearBefore)\n" +
            "\n" +
            "-- 2. Count requests currently active in this window\n" +
            "local currentRequests = redis.call('ZCARD', key)\n" +
            "\n" +
            "-- 3. Atomic check-then-act to prevent race conditions\n" +
            "if currentRequests < maxRequests then\n" +
            "    -- Allowed: record timestamp of this request into sorted set\n" +
            "    redis.call('ZADD', key, now, requestId)\n" +
            "    -- Set key expiration to window size + 5s buffer to prevent memory leakage\n" +
            "    redis.call('EXPIRE', key, math.ceil(windowSizeMs / 1000) + 5)\n" +
            "    local remaining = maxRequests - currentRequests - 1\n" +
            "    return {1, remaining, currentRequests + 1}\n" +
            "else\n" +
            "    -- Blocked: rate limit exceeded\n" +
            "    return {0, 0, currentRequests}\n" +
            "end\n";

        DefaultRedisScript<List> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(script);
        redisScript.setResultType(List.class);
        return redisScript;
    }
}
