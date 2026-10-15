package in.pbl.ApiRateLimiter.service;

import in.pbl.ApiRateLimiter.entity.RequestAuditLog;
import in.pbl.ApiRateLimiter.repository.RequestAuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for Asynchronous Audit Logging.
 * 
 * Demonstrates:
 * - OS Concept: Producer-Consumer Pattern
 *   The incoming Web thread (Producer) dispatches an audit task to the ThreadPoolTaskExecutor queue.
 *   A dedicated worker thread (Consumer) asynchronously persists the record to MySQL.
 *   This ensures the fast-path API response latency is completely decoupled from MySQL disk I/O.
 * - Database Concept: Transaction Propagation (REQUIRES_NEW)
 *   Audit logging runs in an independent database transaction so failure to log does not roll
 *   back successful business operations.
 */
@Service
public class AsyncAuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AsyncAuditLogService.class);
    private final RequestAuditLogRepository auditLogRepository;

    public AsyncAuditLogService(RequestAuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Async("auditLogExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordLog(Long userId, String username, String endpoint, String httpMethod,
                          String clientIp, String status, Long responseTimeMs, Long requestsInWindow) {
        try {
            RequestAuditLog auditLog = new RequestAuditLog(
                    userId,
                    username,
                    endpoint,
                    httpMethod,
                    clientIp,
                    status,
                    responseTimeMs,
                    requestsInWindow
            );
            auditLogRepository.save(auditLog);
            log.debug("Async persisted audit log for user [{}] on endpoint [{}] with status [{}] by thread [{}]",
                    username, endpoint, status, Thread.currentThread().getName());
        } catch (Exception e) {
            log.error("Failed to persist audit log asynchronously", e);
        }
    }
}
