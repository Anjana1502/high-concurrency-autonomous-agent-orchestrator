package com.zaalima.high_concurrency_agent_orchestrator.benchmark;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Exposes the virtual-vs-platform comparison for the final demo. */
@RestController
@RequestMapping("/api/benchmark")
public class BenchmarkController {

    private final BenchmarkService benchmarkService;

    public BenchmarkController(BenchmarkService benchmarkService) {
        this.benchmarkService = benchmarkService;
    }

    @GetMapping
    public List<BenchmarkResult> run(
            @RequestParam(defaultValue = "1000") int tasks,
            @RequestParam(defaultValue = "both") String mode) {
        return switch (mode.toLowerCase()) {
            case "virtual" -> List.of(benchmarkService.run(tasks, true));
            case "platform" -> List.of(benchmarkService.run(tasks, false));
            case "both" -> benchmarkService.runBoth(tasks);
            default -> throw new IllegalArgumentException("mode must be virtual, platform or both");
        };
    }
}
