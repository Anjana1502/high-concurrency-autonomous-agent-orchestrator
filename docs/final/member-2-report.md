# Member 2 — Final Report

Owner: Member 2 (Core Orchestration Engine)
Branch: `feature/member-2-virtual-threads`
Scope: Java 21 virtual threads, structured concurrency, pinning/leak testing, benchmark harness.

## Summary

Built the execution core of the orchestrator. Tasks run one-per-virtual-thread with `ReentrantLock`-based state transitions so carrier threads are never pinned. A structured-concurrency workflow fans out to analytics and user-profile sub-tasks under a single `StructuredTaskScope.ShutdownOnFailure`, with Resilience4j guarding each branch. A JFR-based automated test asserts zero `jdk.VirtualThreadPinned` events, a leak test bounds retained task history, and a benchmark harness compares virtual threads against the platform-thread baseline.

## Deliverables

| Day | Theme | Key artifacts |
|-----|-------|---------------|
| 1–2 | Spring Boot + Java 21 | `pom.xml`, preview API configuration |
| 3–4 | Virtual-thread executor | `VirtualThreadConfig` (virtual/platform switch), `CountingExecutorService` |
| 5–6 | Task service | `AgentTaskService` (`ReentrantLock`, submit/cancel/query) |
| 7–8 | Structured concurrency | `StructuredWorkflowService`, `WorkflowResult`, `WorkflowController` |
| 9–10 | Sub-task simulation | analytics + user-profile sub-tasks with fallback semantics |
| 11–12 | Failure/cancellation | `ShutdownOnFailure`, timeout handling, `WorkflowException` |
| 13–14 | Pinning test | `VirtualThreadPinningTest` (JFR recording + event assertion) |
| 15–16 | Memory-leak test | `TaskHistoryLeakTest`, `pruneCompletedTasks(int)` |
| 17–18 | Benchmark harness | `BenchmarkService`, `BenchmarkController`, `BenchmarkResult` |
| 19–20 | Integration & report | `AgentTaskServiceTest`, executor-mode switch, this report |

## Key design decisions

- **No pinning:** all mutable task state transitions use `java.util.concurrent.locks.ReentrantLock` rather than `synchronized`. Virtual threads park on the lock instead of pinning their carrier.
- **Structured concurrency:** `StructuredTaskScope.ShutdownOnFailure` gives automatic cancellation of sibling sub-tasks when one fails, plus a hard boundary for `joinUntil(timeout)`.
- **Resilience integration:** each sub-task is wrapped in Member 3's `ExternalServiceClient` so circuit breaking, bulkheading, retry and fallback apply uniformly.
- **Bounded history:** `tasks` is a `ConcurrentHashMap`; `pruneCompletedTasks(max)` removes only terminal, non-running tasks, giving callers explicit control over retention.
- **Benchmark modes:** `orchestrator.executor.mode=virtual|platform` switches the application executor, and `GET /api/benchmark?mode=both` runs a 200/1000-task virtual-vs-platform comparison.

## Verification

- `VirtualThreadPinningTest` — JFR records `jdk.VirtualThreadPinned` at threshold 0 ns across 25 concurrent tasks; asserts zero events.
- `TaskHistoryLeakTest` — submits 30 tasks, waits for completion, prunes to a bound of 10 and asserts the map shrinks.
- `StructuredWorkflowServiceTest` — verifies fan-out/fan-in, timeout fail-fast and resilience wiring.
- `BenchmarkServiceTest` — verifies both modes execute with correct metadata and invalid inputs are rejected.
- Full suite: `./mvnw test` — all green.

## Cross-module integration

- `NodeTaskTracker` (Member 1) is notified on task start/finish so node load metrics are accurate.
- `AgentPayloadService` (Member 3) submits through `AgentTaskService`, including the `[node=<id>]` routing marker added by Member 1's balancer.
- Resilience patterns are reused from Member 3's `ExternalServiceClient` rather than reimplemented.

## Known limitations / future work

- `StructuredTaskScope` is a preview API in Java 21, so `--enable-preview` is required at compile and runtime (already configured in `pom.xml` and the Dockerfile).
- The benchmark uses a synthetic 20 ms sleep as an I/O proxy; real workloads should replace it with measured network calls.
- Task results are kept in-memory only; persistence would be needed across node restarts.

## Retrospective

- **Learned:** `synchronized` pins virtual threads under contention; JFR's `VirtualThreadPinned` event gives a precise, automatable gate.
- **Would change:** introduce the executor-mode switch earlier so the platform baseline could be measured during development rather than at the end.
