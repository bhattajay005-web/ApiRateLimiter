package in.pbl.ApiRateLimiter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Thread Pool Configuration.
 * 
 * Demonstrates Operating System Concepts:
 * 1. Producer-Consumer Pattern:
 *    - Incoming HTTP requests (Producers) push audit log events into an in-memory queue.
 *    - Worker threads (Consumers) pick events from the queue and write them to MySQL.
 * 2. Thread Lifecycle & Resource Management:
 *    - Reuses a bounded pool of OS worker threads to eliminate process/thread creation overhead.
 * 3. Graceful Backpressure (CallerRunsPolicy):
 *    - If the internal queue fills up, the producing thread temporarily slows down and executes
 *      the task itself, preventing OutOfMemory (OOM) failures under heavy load.
 * 4. Memory Hierarchy (RAM Fast Path vs Disk Cold Path):
 *    - Rate check happens in RAM (Redis, <1ms). Disk I/O (MySQL, ~20ms) is offloaded asynchronously.
 */
@Configuration
@EnableAsync
public class ThreadPoolConfig {

    @Bean(name = "auditLogExecutor")
    public Executor auditLogExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // Core pool size: threads kept alive in memory
        executor.setCorePoolSize(4);
        // Max pool size: maximum threads under heavy load
        executor.setMaxPoolSize(8);
        // Bounded queue: protects server RAM from exploding under bursts
        executor.setQueueCapacity(500);
        // Thread naming for observability and OS profiling
        executor.setThreadNamePrefix("AuditLogWorker-");
        // Rejection Policy: OS Backpressure mechanism
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
