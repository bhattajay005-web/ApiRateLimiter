package in.pbl.ApiRateLimiter;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.pbl.ApiRateLimiter.dto.LoginRequest;
import in.pbl.ApiRateLimiter.entity.RequestAuditLog;
import in.pbl.ApiRateLimiter.repository.RequestAuditLogRepository;
import in.pbl.ApiRateLimiter.repository.UserRepository;
import in.pbl.ApiRateLimiter.service.ConcurrencyThrottleService;
import in.pbl.ApiRateLimiter.service.RateLimiterService;
import in.pbl.ApiRateLimiter.service.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full End-to-End Integration Test for API Rate Limiter.
 */
@SpringBootTest
public class RateLimiterIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RequestAuditLogRepository auditLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RateLimiterService rateLimiterService;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private ConcurrencyThrottleService concurrencyThrottleService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        // Clean rate limit keys in Redis before each test
        Set<String> keys = redisTemplate.keys("rate_limit:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        Set<String> concurrentKeys = redisTemplate.keys("concurrent_req:*");
        if (concurrentKeys != null && !concurrentKeys.isEmpty()) {
            redisTemplate.delete(concurrentKeys);
        }
    }

    @Test
    @DisplayName("Should login and retrieve session token from MySQL and Redis")
    void testLoginSuccess() throws Exception {
        LoginRequest loginRequest = new LoginRequest("alice", "password123");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("alice"))
                .andExpect(jsonPath("$.data.tier").value("FREE"))
                .andExpect(jsonPath("$.data.maxRequests").value(5))
                .andReturn();

        Map<?, ?> responseMap = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        Map<?, ?> dataMap = (Map<?, ?>) responseMap.get("data");
        String token = (String) dataMap.get("token");

        assertThat(token).isNotBlank();
        // Verify token is cached in Redis
        assertThat(redisTemplate.hasKey("session:" + token)).isTrue();
    }

    @Test
    @DisplayName("Should reject requests with missing or expired session token")
    void testExpiredOrInvalidSession() throws Exception {
        // 1. Missing Token
        mockMvc.perform(get("/api/v1/resource/data"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        // 2. Invalid Token
        mockMvc.perform(get("/api/v1/resource/data")
                        .header("X-Session-Token", "invalid_token_9999"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Session expired")));
    }

    @Test
    @DisplayName("Should enforce Sliding Window Counter rate limit: 5 allowed, 6th returns 429 Too Many Requests")
    void testSlidingWindowRateLimiting() throws Exception {
        // Login as Alice (FREE Tier: 5 requests / 60 seconds)
        LoginRequest loginRequest = new LoginRequest("alice", "password123");
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> responseMap = objectMapper.readValue(loginResult.getResponse().getContentAsString(), Map.class);
        Map<?, ?> dataMap = (Map<?, ?>) responseMap.get("data");
        String token = (String) dataMap.get("token");

        // Execute 5 allowed requests
        for (int i = 1; i <= 5; i++) {
            mockMvc.perform(get("/api/v1/resource/data")
                            .header("X-Session-Token", token))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-RateLimit-Limit", "5"))
                    .andExpect(header().string("X-RateLimit-Remaining", String.valueOf(5 - i)))
                    .andExpect(jsonPath("$.success").value(true));
        }

        // 6th Request: Quota Exceeded -> 429 Too Many Requests
        mockMvc.perform(get("/api/v1/resource/data")
                        .header("X-Session-Token", token))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("X-RateLimit-Remaining", "0"))
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Rate limit quota exceeded")));

        // Give worker thread a moment to finish async database audit logging
        Thread.sleep(500);

        // Verify MySQL Audit Log has recorded both ALLOWED and BLOCKED_RATE_LIMIT
        List<RequestAuditLog> logs = auditLogRepository.findTop50ByOrderByRequestTimestampDesc();
        assertThat(logs).isNotEmpty();
        boolean hasAllowed = logs.stream().anyMatch(l -> "ALLOWED".equals(l.getStatus()));
        boolean hasBlocked = logs.stream().anyMatch(l -> "BLOCKED_RATE_LIMIT".equals(l.getStatus()));
        assertThat(hasAllowed).isTrue();
        assertThat(hasBlocked).isTrue();
    }

    @Test
    @DisplayName("OS Concept: Simultaneous requests hitting at exact same millisecond via CountDownLatch")
    void testConcurrentSimultaneousRequests() throws Exception {
        // Login as Alice (FREE Tier: max 5 requests, max 2 concurrent in-flight)
        LoginRequest loginRequest = new LoginRequest("alice", "password123");
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> responseMap = objectMapper.readValue(loginResult.getResponse().getContentAsString(), Map.class);
        Map<?, ?> dataMap = (Map<?, ?>) responseMap.get("data");
        String token = (String) dataMap.get("token");

        int threadCount = 10;
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
        java.util.concurrent.CountDownLatch startGun = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch doneGate = new java.util.concurrent.CountDownLatch(threadCount);

        java.util.concurrent.atomic.AtomicInteger success200 = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger blocked429 = new java.util.concurrent.atomic.AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startGun.await(); // OS synchronization: all threads block until gun fires
                    MvcResult res = mockMvc.perform(get("/api/v1/resource/data")
                                    .header("X-Session-Token", token))
                            .andReturn();

                    int status = res.getResponse().getStatus();
                    if (status == 200) {
                        success200.incrementAndGet();
                    } else if (status == 429) {
                        blocked429.incrementAndGet();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    doneGate.countDown();
                }
            });
        }

        // Fire the gun: all 10 threads request simultaneously
        startGun.countDown();
        doneGate.await(10, java.util.concurrent.TimeUnit.SECONDS);
        executor.shutdown();

        // Total requests must equal 10
        assertThat(success200.get() + blocked429.get()).isEqualTo(10);
        // Quota is 5 and concurrent limit is 2; at most 5 can succeed, rest get 429
        assertThat(success200.get()).isLessThanOrEqualTo(5);
        assertThat(blocked429.get()).isGreaterThanOrEqualTo(5);
        assertThat(blocked429.get()).isPositive();
    }
}
