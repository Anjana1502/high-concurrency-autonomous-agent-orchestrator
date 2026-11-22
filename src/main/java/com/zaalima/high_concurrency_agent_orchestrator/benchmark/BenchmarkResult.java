package com.zaalima.high_concurrency_agent_orchestrator.benchmark;

/**
 * @param mode              virtual or platform
 * @param tasks             number of simulated I/O tasks executed
 * @param durationMs        wall-clock duration
 * @param throughputPerSec  completed tasks per second
 * @param avgLatencyMs      average per-task wait time
 * @param usedHeapMb        heap used at the end of the run
 */
public record BenchmarkResult(
        String mode,
        int tasks,
        long durationMs,
        double throughputPerSec,
        double avgLatencyMs,
        double usedHeapMb) {
}
