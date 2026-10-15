package in.pbl.ApiRateLimiter.dto;

public class RateLimitResult {
    private final boolean allowed;
    private final long remainingRequests;
    private final long currentRequests;
    private final long limit;
    private final long resetSeconds;
    private final String reason;

    public RateLimitResult(boolean allowed, long remainingRequests, long currentRequests, long limit, long resetSeconds, String reason) {
        this.allowed = allowed;
        this.remainingRequests = remainingRequests;
        this.currentRequests = currentRequests;
        this.limit = limit;
        this.resetSeconds = resetSeconds;
        this.reason = reason;
    }

    public static RateLimitResult allowed(long remaining, long current, long limit, long resetSeconds) {
        return new RateLimitResult(true, remaining, current, limit, resetSeconds, "OK");
    }

    public static RateLimitResult exceeded(long current, long limit, long resetSeconds) {
        return new RateLimitResult(false, 0, current, limit, resetSeconds, "RATE_LIMIT_EXCEEDED");
    }

    public static RateLimitResult concurrencyExceeded(long limit) {
        return new RateLimitResult(false, 0, limit, limit, 1, "CONCURRENCY_LIMIT_EXCEEDED");
    }

    public boolean isAllowed() {
        return allowed;
    }

    public long getRemainingRequests() {
        return remainingRequests;
    }

    public long getCurrentRequests() {
        return currentRequests;
    }

    public long getLimit() {
        return limit;
    }

    public long getResetSeconds() {
        return resetSeconds;
    }

    public String getReason() {
        return reason;
    }
}
