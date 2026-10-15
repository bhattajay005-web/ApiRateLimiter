package in.pbl.ApiRateLimiter.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.pbl.ApiRateLimiter.dto.ApiResponse;
import in.pbl.ApiRateLimiter.dto.RateLimitResult;
import in.pbl.ApiRateLimiter.dto.SessionData;
import in.pbl.ApiRateLimiter.service.AsyncAuditLogService;
import in.pbl.ApiRateLimiter.service.ConcurrencyThrottleService;
import in.pbl.ApiRateLimiter.service.RateLimiterService;
import in.pbl.ApiRateLimiter.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Optional;

/**
 * HTTP Request Interceptor implementing rate limiting, session verification,
 * and concurrent request throttling.
 * 
 * Demonstrates:
 * 1. Session Expiry & Validity Check (Redis In-Memory Fast Path + MySQL Fallback).
 * 2. Simultaneous Request Throttling (OS Semaphore & Bulkhead Pattern via Redis).
 * 3. Atomic Sliding Window Rate Limiting (Redis Lua script - Mutual Exclusion).
 * 4. Asynchronous Producer-Consumer Audit Logging to MySQL.
 */
@Component
public class RateLimiterInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterInterceptor.class);

    public static final String ATTR_START_TIME = "X_START_TIME";
    public static final String ATTR_SESSION_DATA = "X_SESSION_DATA";
    public static final String ATTR_CONCURRENCY_ACQUIRED = "X_CONCURRENCY_ACQUIRED";
    public static final String ATTR_RATE_RESULT = "X_RATE_RESULT";

    private final SessionService sessionService;
    private final RateLimiterService rateLimiterService;
    private final ConcurrencyThrottleService concurrencyThrottleService;
    private final AsyncAuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public RateLimiterInterceptor(SessionService sessionService,
                                  RateLimiterService rateLimiterService,
                                  ConcurrencyThrottleService concurrencyThrottleService,
                                  AsyncAuditLogService auditLogService,
                                  ObjectMapper objectMapper) {
        this.sessionService = sessionService;
        this.rateLimiterService = rateLimiterService;
        this.concurrencyThrottleService = concurrencyThrottleService;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        long startTime = System.currentTimeMillis();
        request.setAttribute(ATTR_START_TIME, startTime);

        String endpoint = request.getRequestURI();
        String method = request.getMethod();
        String clientIp = extractClientIp(request);

        // 1. Extract Session Token
        String token = extractToken(request);
        if (token == null || token.isBlank()) {
            rejectWithJson(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Authentication required: Provide a valid session token in 'X-Session-Token' or 'Authorization: Bearer <token>' header.");
            auditLogService.recordLog(null, "ANONYMOUS", endpoint, method, clientIp,
                    "UNAUTHORIZED", System.currentTimeMillis() - startTime, 0L);
            return false;
        }

        // 2. Validate Session & Check Expiration
        Optional<SessionData> sessionOpt = sessionService.validateSession(token);
        if (sessionOpt.isEmpty()) {
            rejectWithJson(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "Session expired, invalid, or revoked. Please log in again to obtain a new session token.");
            auditLogService.recordLog(null, "UNKNOWN", endpoint, method, clientIp,
                    "SESSION_EXPIRED", System.currentTimeMillis() - startTime, 0L);
            return false;
        }

        SessionData session = sessionOpt.get();
        request.setAttribute(ATTR_SESSION_DATA, session);

        // 3. Track and Throttle Simultaneous Requests (OS Bulkhead / Semaphore Concept)
        boolean permitAcquired = concurrencyThrottleService.tryAcquire(session.getUserId(), session.getMaxConcurrentRequests());
        if (!permitAcquired) {
            response.setHeader("Retry-After", "1");
            response.setHeader("X-RateLimit-Error", "CONCURRENCY_LIMIT_EXCEEDED");
            rejectWithJson(response, 429,
                    "Too many simultaneous requests. Your tier [" + session.getTierName() +
                    "] allows a maximum of " + session.getMaxConcurrentRequests() + " concurrent in-flight requests.");
            auditLogService.recordLog(session.getUserId(), session.getUsername(), endpoint, method, clientIp,
                    "BLOCKED_CONCURRENCY", System.currentTimeMillis() - startTime, 0L);
            return false;
        }
        request.setAttribute(ATTR_CONCURRENCY_ACQUIRED, Boolean.TRUE);

        // 4. Sliding Window Rate Limiting Check (Atomic Redis Lua Script)
        RateLimitResult rateResult = rateLimiterService.checkRateLimit(
                session.getUserId(),
                session.getMaxRequests(),
                session.getWindowSeconds()
        );
        request.setAttribute(ATTR_RATE_RESULT, rateResult);

        // Populate Standard Rate Limit Headers
        response.setHeader("X-RateLimit-Limit", String.valueOf(rateResult.getLimit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(rateResult.getRemainingRequests()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(rateResult.getResetSeconds()));
        response.setHeader("X-RateLimit-Tier", session.getTierName());

        if (!rateResult.isAllowed()) {
            // Roll back in-flight concurrency permit since request will not execute
            concurrencyThrottleService.release(session.getUserId());
            request.removeAttribute(ATTR_CONCURRENCY_ACQUIRED);

            response.setHeader("Retry-After", String.valueOf(rateResult.getResetSeconds()));
            rejectWithJson(response, 429,
                    "Rate limit quota exceeded! Tier [" + session.getTierName() + "] allows " +
                    session.getMaxRequests() + " requests per " + session.getWindowSeconds() + " seconds. " +
                    "Current window count: " + rateResult.getCurrentRequests());

            auditLogService.recordLog(session.getUserId(), session.getUsername(), endpoint, method, clientIp,
                    "BLOCKED_RATE_LIMIT", System.currentTimeMillis() - startTime, rateResult.getCurrentRequests());
            return false;
        }

        // Request passed all checks: Session active + Concurrency OK + Rate limit within bounds
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        Long startTime = (Long) request.getAttribute(ATTR_START_TIME);
        SessionData session = (SessionData) request.getAttribute(ATTR_SESSION_DATA);
        Boolean concurrencyAcquired = (Boolean) request.getAttribute(ATTR_CONCURRENCY_ACQUIRED);
        RateLimitResult rateResult = (RateLimitResult) request.getAttribute(ATTR_RATE_RESULT);

        // Release concurrency permit
        if (Boolean.TRUE.equals(concurrencyAcquired) && session != null) {
            concurrencyThrottleService.release(session.getUserId());
        }

        // Asynchronously persist ALLOWED audit log to MySQL
        if (session != null && startTime != null && rateResult != null && rateResult.isAllowed()) {
            long durationMs = System.currentTimeMillis() - startTime;
            long currentReqs = rateResult.getCurrentRequests();
            String endpoint = request.getRequestURI();
            String method = request.getMethod();
            String clientIp = extractClientIp(request);

            auditLogService.recordLog(session.getUserId(), session.getUsername(), endpoint, method,
                    clientIp, "ALLOWED", durationMs, currentReqs);
        }
    }

    private String extractToken(HttpServletRequest request) {
        String token = request.getHeader("X-Session-Token");
        if (token != null && !token.isBlank()) {
            return token.trim();
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }

        return request.getParameter("token");
    }

    private String extractClientIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        if (xf != null && !xf.isBlank()) {
            return xf.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void rejectWithJson(HttpServletResponse response, int statusCode, String message) throws IOException {
        response.setStatus(statusCode);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiResponse<Void> errorResponse = ApiResponse.error(message);
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
