package com.zaalima.high_concurrency_agent_orchestrator.benchmark;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Week 4 benchmark harness comparing Java 21 virtual threads with the legacy
 * platform-thread baseline. Each task simulates a 20 ms I/O wait.
 */
@Service
public class BenchmarkService {

    private static final Logger logger = LoggerFactory.getLogger(BenchmarkService.class);

    private static final int IO_WAIT_MS = 20;
    private static final int PLATFORM_POOL_SIZE = 200;

    public BenchmarkResult run(int taskCount, boolean virtual) {
        if (taskCount < 1 || taskCount > 100_000) {
            throw new IllegalArgumentException("taskCount must be between 1 and 100000");
        }

        ExecutorService executor = virtual
                ? Executors.newVirtualThreadPerTaskExecutor()
                : Executors.newFixedThreadPool(PLATFORM_POOL_SIZE);

        CountDownLatch latch = new CountDownLatch(taskCount);
        AtomicLong totalLatency = new AtomicLong();
        long started = System.nanoTime();

        try {
            for (int i = 0; i < taskCount; i++) {
                executor.execute(() -> {
                    long taskStart = System.nanoTime();
                    try {
                        Thread.sleep(IO_WAIT_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        totalLatency.addAndGet(
                                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - taskStart));
                        latch.countDown();
                    }
                });
            }
            boolean completed = latch.await(60, TimeUnit.SECONDS);
            if (!completed) {
                throw new IllegalStateException(
                        "Benchmark did not finish within 60s for " + taskCount + " tasks");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Benchmark interrupted", e);
        } finally {
            executor.close();
        }

        long durationMs = Math.max(1, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
        Runtime runtime = Runtime.getRuntime();
        double usedHeapMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024.0 * 1024.0);

        BenchmarkResult result = new BenchmarkResult(
                virtual ? "virtual" : "platform",
                taskCount,
                durationMs,
                taskCount * 1000.0 / durationMs,
                totalLatency.get() / (double) taskCount,
                usedHeapMb);

        logger.info("Benchmark result: {}", result);
        return result;
    }

    public List<BenchmarkResult> runBoth(int taskCount) {
        List<BenchmarkResult> results = new ArrayList<>(2);
        results.add(run(taskCount, false));
        results.add(run(taskCount, true));
        return List.copyOf(results);
    }
}
