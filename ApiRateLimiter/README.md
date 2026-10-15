# 🚀 Production-Grade API Rate Limiter

A high-performance, distributed API Rate Limiter built with **Spring Boot**, **Redis**, and **MySQL**. It enforces the **Sliding Window Counter / Log Algorithm**, manages user session lifetimes, throttles simultaneous concurrent in-flight requests, and incorporates core **Operating System** and **Database System** principles.

---

## 🏛️ High-Level Architecture

```
[ Client Requests ]
       │
       ▼
[ Spring MVC Filter / Interceptor ] ────────┐
  ├── 1. Session Expiry Check               │
  │      └─ In-Memory RAM (Redis)            │ (Rejects 401 if Expired)
  │                                         │
  ├── 2. Simultaneous Request Throttling     │
  │      └─ Semaphore & Redis In-Flight     │ (Rejects 429 if Concurrency Exceeded)
  │                                         │
  ├── 3. Sliding Window Rate Limiting       │
  │      └─ Atomic Redis Lua Script         │ (Rejects 429 if Quota Exceeded)
  │                                         │
  └── 4. Pass Request to Controller ────────┘
         │
         ▼
[ Protected Business Logic Controller ]
         │
         ▼
[ Async ThreadPoolExecutor (Producer-Consumer) ]
         │
         ▼
[ Persistent Storage (MySQL InnoDB Disk) ]
  ├── users
  ├── rate_limit_tiers
  ├── user_sessions
  └── request_audit_logs
```

---

## ⏱️ Sliding Window Counter Algorithm

### The Flaw of Fixed Window Counters
In a standard Fixed Window algorithm (e.g., 5 requests per minute from 12:00 to 12:01):
- A client can send 5 requests at **12:00:59** and 5 more at **12:01:01**.
- Over a 2-second period across the boundary, the server receives **10 requests (2x the burst limit)**.

### The Sliding Window Solution
The Sliding Window algorithm continuously slides the evaluation window $W$:
$$\text{Window Interval} = [T_{\text{now}} - W, \; T_{\text{now}}]$$

In our implementation:
1. Every request's millisecond timestamp is tracked in a Redis Sorted Set (`ZSET`).
2. Timestamps older than $(T_{\text{now}} - W)$ are evicted via `ZREMRANGEBYSCORE`.
3. Total remaining requests are counted via `ZCARD`.
4. If $\text{count} < \text{maxRequests}$, the current request is recorded via `ZADD`, the TTL is renewed, and the request is allowed.
5. If $\text{count} \ge \text{maxRequests}$, the request is rejected with `429 Too Many Requests`.

---

## 🖥️ Operating System Concepts Applied

| OS Concept | Implementation in this System |
|---|---|
| **Mutual Exclusion & Critical Sections** | Multiple threads hitting the rate limiter concurrently can cause check-then-act race conditions. Redis executes our Lua script atomically on its event loop, ensuring **Mutual Exclusion** without multi-step race conditions. |
| **Semaphores & Bulkhead Pattern** | Prevents a single abusive user from consuming all Tomcat worker threads by sending 100 simultaneous requests. The `ConcurrencyThrottleService` uses a distributed in-flight counter and Java `Semaphore` to restrict simultaneous in-flight requests per user. |
| **Producer-Consumer Pattern** | The HTTP processing thread (Producer) pushes audit log records to an in-memory queue. A dedicated `ThreadPoolTaskExecutor` (Consumers) writes records to MySQL asynchronously. |
| **Thread Management & Backpressure** | Bounded thread pool (`core=4, max=8, queue=500`) with `CallerRunsPolicy` prevents memory exhaustion (OOM) under extreme traffic surges. |
| **Memory Hierarchy & Latency** | Demonstrates the latency gap: **RAM Fast Path** (Redis read/write: ~0.5ms) vs **Disk Cold Path** (MySQL B+ Tree I/O: ~15-30ms). Real-time checks always execute in RAM. |

---

## 🗄️ Database Concepts Applied

| Database Concept | Implementation in this System |
|---|---|
| **B+ Tree Indexing** | Composite indexes on `(user_id, request_timestamp)` and `(user_id, expires_at)`, plus unique indexes on `(api_key)` and `(token)` optimize queries to $O(\log N)$. |
| **ACID Transactions** | Session creation and persistence use `@Transactional(isolation = Isolation.READ_COMMITTED)`. Async audit logging uses `Propagation.REQUIRES_NEW`. |
| **Optimistic Locking** | `@Version` column on `RateLimitTier` prevents lost update anomalies when administrators modify tier quotas concurrently. |
| **Connection Pooling (HikariCP)** | HikariCP pool manages open OS socket descriptors to MySQL, eliminating TCP three-way handshake overhead per HTTP query. |
| **Cache-Aside Pattern** | Session validation reads from Redis RAM first. On a cache miss, MySQL disk is queried and the result is written through to Redis with remaining TTL. |

---

## 👥 Pre-Configured Rate Limit Tiers

| Tier Name | Max Requests | Window (Seconds) | Max Concurrent (Simultaneous) | Demo User | Password | API Key |
|---|---|---|---|---|---|---|
| **FREE** | 5 | 60 | 2 | `alice` | `password123` | `key_alice_free_123` |
| **PRO** | 20 | 60 | 5 | `bob` | `password123` | `key_bob_pro_456` |
| **ENTERPRISE** | 100 | 60 | 20 | `charlie` | `password123` | `key_charlie_ent_789` |

---

## 🚀 Running & Testing the Application

### 1. Start MySQL & Redis
Ensure MySQL is running on port 3306 and Redis is running on port 6379:
```powershell
# Redis is already installed via winget and running locally on port 6379
# MySQL is running on port 3306
```

### 2. Run the Spring Boot App
```powershell
cd c:\Users\hp\Downloads\ApiRateLimiter\ApiRateLimiter
.\mvnw.cmd spring-boot:run
```

### 3. Login to Obtain Session Token
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "alice", "password": "password123"}'
```
**Response:**
```json
{
  "success": true,
  "message": "Login successful. Session established in Redis & MySQL.",
  "data": {
    "token": "2f6927fef63f46f481c0bc235e165d21",
    "username": "alice",
    "tier": "FREE",
    "maxRequests": 5,
    "windowSeconds": 60,
    "maxConcurrentRequests": 2
  }
}
```

### 4. Access Protected Endpoint (Within Quota)
```bash
curl -i -X GET http://localhost:8080/api/v1/resource/data \
  -H "X-Session-Token: 2f6927fef63f46f481c0bc235e165d21"
```
**Response Headers:**
```http
HTTP/1.1 200 OK
X-RateLimit-Limit: 5
X-RateLimit-Remaining: 4
X-RateLimit-Reset: 60
X-RateLimit-Tier: FREE
```

### 5. Exceed Rate Limit (6th Request)
After 5 requests in 60 seconds:
```http
HTTP/1.1 429 Too Many Requests
Retry-After: 60
X-RateLimit-Limit: 5
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 60

{
  "success": false,
  "message": "Rate limit quota exceeded! Tier [FREE] allows 5 requests per 60 seconds. Current window count: 5"
}
```

### 6. View Live Metrics & Audit Logs from MySQL
```bash
# Tiers
curl -X GET http://localhost:8080/api/rates/tiers

# Aggregated Stats
curl -X GET http://localhost:8080/api/rates/stats

# Recent 50 Asynchronous MySQL Audit Logs
curl -X GET http://localhost:8080/api/rates/audit-logs
```
