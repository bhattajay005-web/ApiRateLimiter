package in.pbl.ApiRateLimiter.controller;

import in.pbl.ApiRateLimiter.dto.ApiResponse;
import in.pbl.ApiRateLimiter.entity.RateLimitTier;
import in.pbl.ApiRateLimiter.entity.RequestAuditLog;
import in.pbl.ApiRateLimiter.repository.RateLimitTierRepository;
import in.pbl.ApiRateLimiter.repository.RequestAuditLogRepository;
import in.pbl.ApiRateLimiter.service.ConcurrencyThrottleService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller providing visibility into rate limiter configuration,
 * database audit logs, and in-flight concurrency metrics.
 */
@RestController
@RequestMapping("/api/rates")
public class AdminMetricsController {

    private final RateLimitTierRepository tierRepository;
    private final RequestAuditLogRepository auditLogRepository;
    private final ConcurrencyThrottleService concurrencyThrottleService;

    public AdminMetricsController(RateLimitTierRepository tierRepository,
                                  RequestAuditLogRepository auditLogRepository,
                                  ConcurrencyThrottleService concurrencyThrottleService) {
        this.tierRepository = tierRepository;
        this.auditLogRepository = auditLogRepository;
        this.concurrencyThrottleService = concurrencyThrottleService;
    }

    @GetMapping("/tiers")
    public ResponseEntity<ApiResponse<List<RateLimitTier>>> getTiers() {
        List<RateLimitTier> tiers = tierRepository.findAll();
        return ResponseEntity.ok(ApiResponse.ok("Configured Rate Limit Tiers retrieved from MySQL.", tiers));
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<ApiResponse<List<RequestAuditLog>>> getRecentAuditLogs() {
        List<RequestAuditLog> logs = auditLogRepository.findTop50ByOrderByRequestTimestampDesc();
        return ResponseEntity.ok(ApiResponse.ok("Top 50 asynchronous audit logs from MySQL.", logs));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getRateLimiterStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalAllowed", auditLogRepository.countByStatus("ALLOWED"));
        stats.put("totalRateLimited", auditLogRepository.countByStatus("BLOCKED_RATE_LIMIT"));
        stats.put("totalConcurrencyBlocked", auditLogRepository.countByStatus("BLOCKED_CONCURRENCY"));
        stats.put("totalSessionExpired", auditLogRepository.countByStatus("SESSION_EXPIRED"));
        stats.put("totalUnauthorized", auditLogRepository.countByStatus("UNAUTHORIZED"));
        stats.put("totalAuditedRequests", auditLogRepository.count());

        return ResponseEntity.ok(ApiResponse.ok("Aggregated rate limiter metrics from MySQL.", stats));
    }

    @GetMapping("/in-flight/{userId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getInFlight(@PathVariable Long userId) {
        long inFlight = concurrencyThrottleService.getCurrentInFlight(userId);
        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        data.put("activeConcurrentRequests", inFlight);
        return ResponseEntity.ok(ApiResponse.ok("Real-time in-flight requests from Redis.", data));
    }
}
