package in.pbl.ApiRateLimiter.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;

/**
 * Cached session payload stored in Redis for sub-millisecond lookup.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SessionData implements Serializable {
    private static final long serialVersionUID = 1L;

    private String token;
    private Long userId;
    private String username;
    private String apiKey;
    private String tierName;
    private int maxRequests;
    private int windowSeconds;
    private int maxConcurrentRequests;
    private long expiresAtEpochMs;

    public SessionData() {
    }

    public SessionData(String token, Long userId, String username, String apiKey, String tierName,
                       int maxRequests, int windowSeconds, int maxConcurrentRequests,
                       long expiresAtEpochMs) {
        this.token = token;
        this.userId = userId;
        this.username = username;
        this.apiKey = apiKey;
        this.tierName = tierName;
        this.maxRequests = maxRequests;
        this.windowSeconds = windowSeconds;
        this.maxConcurrentRequests = maxConcurrentRequests;
        this.expiresAtEpochMs = expiresAtEpochMs;
    }

    @JsonIgnore
    public boolean isExpired() {
        return System.currentTimeMillis() >= expiresAtEpochMs;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
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

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getTierName() {
        return tierName;
    }

    public void setTierName(String tierName) {
        this.tierName = tierName;
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

    public long getExpiresAtEpochMs() {
        return expiresAtEpochMs;
    }

    public void setExpiresAtEpochMs(long expiresAtEpochMs) {
        this.expiresAtEpochMs = expiresAtEpochMs;
    }
}
