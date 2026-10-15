package in.pbl.ApiRateLimiter.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Entity representing a Rate Limit Tier.
 * 
 * Demonstrates Database Concepts:
 * - Optimistic Locking (@Version): Prevents lost updates when administrators modify tier quotas concurrently.
 * - Relational Mapping: Serves as the one-to-many parent for User rate limiting policies.
 */
@Entity
@Table(name = "rate_limit_tiers")
public class RateLimitTier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Column(name = "max_requests", nullable = false)
    private int maxRequests;

    @Column(name = "window_seconds", nullable = false)
    private int windowSeconds;

    @Column(name = "max_concurrent_requests", nullable = false)
    private int maxConcurrentRequests;

    /**
     * Database Concept: Optimistic Locking
     * If two admins update the quota simultaneously, Hibernate checks the version number.
     * Prevents race conditions at the database level.
     */
    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public RateLimitTier() {
    }

    public RateLimitTier(String name, int maxRequests, int windowSeconds, int maxConcurrentRequests) {
        this.name = name;
        this.maxRequests = maxRequests;
        this.windowSeconds = windowSeconds;
        this.maxConcurrentRequests = maxConcurrentRequests;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getMaxRequests() {
        return maxRequests;
    }

    public void setMaxRequests(int maxRequests) {
        this.maxRequests = maxRequests;
    }

    public int getWindowSeconds() {
        return windowSeconds;
    }

    public void setWindowSeconds(int windowSeconds) {
        this.windowSeconds = windowSeconds;
    }

    public int getMaxConcurrentRequests() {
        return maxConcurrentRequests;
    }

    public void setMaxConcurrentRequests(int maxConcurrentRequests) {
        this.maxConcurrentRequests = maxConcurrentRequests;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
