package in.pbl.ApiRateLimiter.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Entity representing an API Consumer / User.
 * 
 * Demonstrates Database Concepts:
 * - Unique Indexing on username and api_key (B+ Tree fast lookup).
 * - Foreign Key constraint to RateLimitTier.
 */
@Entity
@Table(
    name = "users",
    indexes = {
        @Index(name = "idx_user_apikey", columnList = "api_key", unique = true),
        @Index(name = "idx_user_username", columnList = "username", unique = true)
    }
)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String username;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(name = "api_key", nullable = false, unique = true, length = 64)
    private String apiKey;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "tier_id", nullable = false)
    private RateLimitTier rateLimitTier;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public User() {
    }

    public User(String username, String email, String password, String apiKey, RateLimitTier rateLimitTier) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.apiKey = apiKey;
        this.rateLimitTier = rateLimitTier;
        this.active = true;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public RateLimitTier getRateLimitTier() {
        return rateLimitTier;
    }

    public void setRateLimitTier(RateLimitTier rateLimitTier) {
        this.rateLimitTier = rateLimitTier;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
