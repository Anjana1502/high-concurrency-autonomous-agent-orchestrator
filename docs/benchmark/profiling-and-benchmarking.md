# Profiling and Benchmarking Guide

## 1. Built-in benchmark endpoint

The application exposes a synthetic I/O benchmark (20 ms per task) that compares Java 21 virtual threads with a fixed platform-thread pool of 200.

```bash
# Both modes, 1000 tasks each
curl "http://localhost:8080/api/benchmark?tasks=1000&mode=both"

# One mode
curl "http://localhost:8080/api/benchmark?tasks=5000&mode=virtual"
```

Response shape:

```json
[
  {
    "mode": "platform",
    "tasks": 1000,
    "durationMs": 11200,
    "throughputPerSec": 89.28,
    "avgLatencyMs": 2241.0,
    "usedHeapMb": 142.5
  },
  {
    "mode": "virtual",
    "tasks": 1000,
    "durationMs": 1080,
    "throughputPerSec": 925.9,
    "avgLatencyMs": 24.1,
    "usedHeapMb": 168.3
  }
]
```

Capture results for the report:

```bash
curl -s "http://localhost:8080/api/benchmark?tasks=1000&mode=both" \
  | tee benchmark-results-1000.json
```

## 2. Switching the application executor

Set `orchestrator.executor.mode=platform` (or `ORCHESTRATOR_EXECUTOR_MODE=platform` in Docker) to run the whole application on the legacy pool, then run the 10k load test to compare end-to-end behaviour.

## 3. JFR profiling

Start a recording against a running node:

```bash
# Find the Java PID
jcmd

# Start a 60-second recording with virtual-thread events
jcmd <PID> JFR.start name=orb settings=profile \
  filename=orchestrator.jfr duration=60s \
  settings=profile
```

Enable low-threshold pinning and virtual-thread scheduler events explicitly:

```bash
jcmd <PID> JFR.configure \
  jdk.VirtualThreadPinned#enabled=true \
  jdk.VirtualThreadPinned#threshold=0ns \
  jdk.VirtualThreadSubmitFailed#enabled=true
```

Open `orchestrator.jfr` in JDK Mission Control and inspect:

- **Java Virtual Threads → Virtual Thread Pinned** — must be empty.
- **Java Virtual Threads → Virtual Thread Submit Failed** — indicates executor saturation.
- **Memory → Garbage Collections** — correlate with `TaskHistoryLeakTest` retention bounds.

The automated gate `VirtualThreadPinningTest` runs the same check inside the test suite so pinning regressions fail CI.

## 4. Memory-leak verification

```bash
./mvnw -B test -Dtest=TaskHistoryLeakTest
```

The test submits 30 tasks, waits for completion, prunes to a bound of 10 and asserts the retained task map shrinks. Run it repeatedly with `-Dsurefire.rerunFailingTestsCount=3` to check stability.

## 5. Load test

```bash
./mvnw -B test -Dtest=TenThousandAgentLoadTest -Dagent.load.test=true
```

This drives 10,000 agent registrations and 20,000 payload submissions. See [docs/loadtest/10k-load-test-results.md](../loadtest/10k-load-test-results.md) for the recorded baseline.

## 6. Interpreting results

| Metric | Meaning | Healthy signal |
|--------|---------|----------------|
| `throughputPerSec` | Tasks completed per second | Virtual mode substantially higher for I/O-bound work |
| `avgLatencyMs` | Mean per-task wait | Close to the 20 ms I/O proxy for virtual threads |
| `usedHeapMb` | Heap occupancy at end of run | Stable across repeated runs (no leak) |
| Pinned events | Times a virtual thread blocked on a monitor | Exactly zero |
