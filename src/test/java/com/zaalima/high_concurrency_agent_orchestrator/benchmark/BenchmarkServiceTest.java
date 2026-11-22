package com.zaalima.high_concurrency_agent_orchestrator.benchmark;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BenchmarkServiceTest {

    private final BenchmarkService benchmark = new BenchmarkService();

    @Test
    void comparesVirtualAndPlatformExecutors() {
        List<BenchmarkResult> results = benchmark.runBoth(200);

        assertEquals(2, results.size());
        assertEquals("platform", results.get(0).mode());
        assertEquals("virtual", results.get(1).mode());
        results.forEach(result -> {
            assertEquals(200, result.tasks());
            assertTrue(result.durationMs() > 0);
            assertTrue(result.throughputPerSec() > 0);
            assertTrue(result.avgLatencyMs() >= 20);
        });
    }

    @Test
    void rejectsInvalidTaskCounts() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> benchmark.run(0, true));
    }
}
