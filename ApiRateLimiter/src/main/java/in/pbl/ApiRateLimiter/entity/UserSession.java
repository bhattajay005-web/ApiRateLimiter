package in.pbl.ApiRateLimiter.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Entity representing an active user session.
 * 
 * Demonstrates Database Concepts:
 * - Composite Index on (token, expires_at) for efficient expiry filtering in MySQL.
 * - Soft invalidation via isRevoked flag.
 */
@Entity
@Table(
    name = "user_sessions",
    indexes = {
        @Index(name = "idx_session_token", columnList = "token", unique = true),
        @Index(name = "idx_session_user_expiry", columnList = "user_id, expires_at")
    }
)
public class UserSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 128)
    private String token;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "is_revoked", nullable = false)
    private boolean isRevoked = false;

    public UserSession() {
    }

    public UserSession(String token, User user, Instant expiresAt) {
        this.token = token;
        this.user = user;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
        this.isRevoked = false;
    }

    public boolean isExpired() {
        return isRevoked || Instant.now().isAfter(expiresAt);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isRevoked() {
        return isRevoked;
    }

    public void setRevoked(boolean revoked) {
        isRevoked = revoked;
    }
}
