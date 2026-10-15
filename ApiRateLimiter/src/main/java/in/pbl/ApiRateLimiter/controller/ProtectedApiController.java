package in.pbl.ApiRateLimiter.controller;

import in.pbl.ApiRateLimiter.dto.ApiResponse;
import in.pbl.ApiRateLimiter.dto.RateLimitResult;
import in.pbl.ApiRateLimiter.dto.SessionData;
import in.pbl.ApiRateLimiter.interceptor.RateLimiterInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Business API protected by Sliding Window Rate Limiting, Session Expiration,
 * and Concurrency Throttling.
 */
@RestController
@RequestMapping("/api/v1/resource")
public class ProtectedApiController {

    /**
     * Standard protected business endpoint.
     */
    @GetMapping("/data")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getProtectedData(HttpServletRequest request) {
        SessionData session = (SessionData) request.getAttribute(RateLimiterInterceptor.ATTR_SESSION_DATA);
        RateLimitResult rateResult = (RateLimitResult) request.getAttribute(RateLimiterInterceptor.ATTR_RATE_RESULT);

        Map<String, Object> data = new HashMap<>();
        data.put("message", "Access granted to high-value protected resource!");
        data.put("username", session != null ? session.getUsername() : "unknown");
        data.put("tier", session != null ? session.getTierName() : "unknown");
        data.put("currentWindowUsage", rateResult != null ? rateResult.getCurrentRequests() : 1);
        data.put("remainingQuota", rateResult != null ? rateResult.getRemainingRequests() : 0);
        data.put("maxRequestsPerWindow", session != null ? session.getMaxRequests() : 0);
        data.put("windowSeconds", session != null ? session.getWindowSeconds() : 60);

        return ResponseEntity.ok(ApiResponse.ok("Request processed successfully.", data));
    }

    /**
     * Endpoint simulating heavy I/O or CPU computation to test simultaneous request concurrency.
     * 
     * Demonstrates OS Concept:
     * - Thread suspension (Thread.sleep) simulating I/O wait.
     * - Clients sending requests at the same time can observe the ConcurrencyThrottleService
     *   limiting concurrent in-flight requests.
     */
    @GetMapping("/heavy-computation")
    public ResponseEntity<ApiResponse<Map<String, Object>>> heavyComputation(
            @RequestParam(defaultValue = "500") long delayMs,
            HttpServletRequest request) {

        SessionData session = (SessionData) request.getAttribute(RateLimiterInterceptor.ATTR_SESSION_DATA);

        try {
            // Simulate OS thread performing I/O or compute
            long actualDelay = Math.min(Math.max(delayMs, 50), 3000);
            Thread.sleep(actualDelay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Map<String, Object> data = new HashMap<>();
        data.put("message", "Computation complete.");
        data.put("executedByThread", Thread.currentThread().getName());
        data.put("simulatedLatencyMs", delayMs);
        data.put("username", session != null ? session.getUsername() : "unknown");

        return ResponseEntity.ok(ApiResponse.ok("Heavy computation finished.", data));
    }
}
