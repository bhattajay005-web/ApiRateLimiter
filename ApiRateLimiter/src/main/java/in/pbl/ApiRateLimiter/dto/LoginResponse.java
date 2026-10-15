package in.pbl.ApiRateLimiter.dto;

import java.time.Instant;

public class LoginResponse {
    private String token;
    private String username;
    private String tier;
    private int maxRequests;
    private int windowSeconds;
    private int maxConcurrentRequests;
    private Instant expiresAt;

    public LoginResponse() {
    }

    public LoginResponse(String token, String username, String tier, int maxRequests,
                         int windowSeconds, int maxConcurrentRequests, Instant expiresAt) {
        this.token = token;
        this.username = username;
        this.tier = tier;
        this.maxRequests = maxRequests;
        this.windowSeconds = windowSeconds;
        this.maxConcurrentRequests = maxConcurrentRequests;
        this.expiresAt = expiresAt;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getTier() {
        return tier;
    }

    public void setTier(String tier) {
        this.tier = tier;
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

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
}
