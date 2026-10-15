package in.pbl.ApiRateLimiter.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Entity for recording incoming API requests asynchronously in MySQL.
 * 
 * Demonstrates:
 * - OS Concept: Producer-Consumer pattern (HTTP threads produce logs, background worker threads consume and persist).
 * - Database Concept: Audit logging, range-query indexing on (user_id, request_timestamp).
 */
@Entity
@Table(
    name = "request_audit_logs",
    indexes = {
        @Index(name = "idx_audit_user_time", columnList = "user_id, request_timestamp"),
        @Index(name = "idx_audit_status", columnList = "status")
    }
)
public class RequestAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "username", length = 100)
    private String username;

    @Column(name = "endpoint", nullable = false, length = 255)
    private String endpoint;

    @Column(name = "http_method", nullable = false, length = 10)
    private String httpMethod;

    @Column(name = "client_ip", length = 50)
    private String clientIp;

    @Column(name = "request_timestamp", nullable = false)
    private Instant requestTimestamp = Instant.now();

    /**
     * ALLOWED, BLOCKED_RATE_LIMIT, BLOCKED_CONCURRENCY, SESSION_EXPIRED, UNAUTHORIZED
     */
    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "response_time_ms")
    private Long responseTimeMs;

    @Column(name = "requests_in_window")
    private Long requestsInWindow;

    public RequestAuditLog() {
    }

    public RequestAuditLog(Long userId, String username, String endpoint, String httpMethod,
                           String clientIp, String status, Long responseTimeMs, Long requestsInWindow) {
        this.userId = userId;
        this.username = username;
        this.endpoint = endpoint;
        this.httpMethod = httpMethod;
        this.clientIp = clientIp;
        this.requestTimestamp = Instant.now();
        this.status = status;
        this.responseTimeMs = responseTimeMs;
        this.requestsInWindow = requestsInWindow;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public Instant getRequestTimestamp() {
        return requestTimestamp;
    }

    public void setRequestTimestamp(Instant requestTimestamp) {
        this.requestTimestamp = requestTimestamp;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getResponseTimeMs() {
        return responseTimeMs;
    }

    public void setResponseTimeMs(Long responseTimeMs) {
        this.responseTimeMs = responseTimeMs;
    }

    public Long getRequestsInWindow() {
        return requestsInWindow;
    }

    public void setRequestsInWindow(Long requestsInWindow) {
        this.requestsInWindow = requestsInWindow;
    }
}
