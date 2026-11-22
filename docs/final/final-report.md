# Final Report — High-Concurrency Autonomous Agent Orchestrator

Team: 4 members
Duration: 20 working days
Stack: Java 21 (Virtual Threads, Structured Concurrency), Spring Boot 4, Redis, REST + gRPC, Resilience4j, Docker Compose, GitHub Actions, JUnit/Testcontainers.

## Project outcome

The system coordinates a large number of autonomous agent workflows across a horizontally scalable cluster. All four member workstreams are integrated on the main baseline:

| Member | Area | Delivered |
|--------|------|-----------|
| [Member 1](member-1-report.md) | Redis & cluster coordination | K/V + TTL, Pub/Sub, atomic distributed locks, node registry, least-utilised-node balancer, metrics |
| [Member 2](member-2-report.md) | Core orchestration engine | Virtual-thread executor with no pinning, structured workflows, cancellation/failure handling, leak bounds, benchmarks |
| [Member 3](member-3-report.md) | Interfaces & resilience | REST + gRPC, registration security, agent registry, payload flow, simulator, 10k load test, Resilience4j, dashboard |
| [Member 4](member-4-report.md) | Infrastructure & testing | Multi-stage Docker image, 3-node Compose cluster, CI quality gates, profiling/benchmarking guides, deployment guide |

## Milestone summary

| Week | Focus | Result |
|------|-------|--------|
| 1 | Foundations | Repository rules, Redis connectivity, Java 21 executor, REST/gRPC contracts, CI skeleton |
| 2 | Concurrency & coordination | Pub/Sub, locks, node heartbeats, structured concurrency, pinning/leak tests |
| 3 | Interfaces & resilience | Payload routing, registry sharing, resilience patterns, dashboard, integration tests |
| 4 | Scale & delivery | Load balancer integration, benchmark harness, Docker cluster, deployment docs, final demo |

## Verification evidence

- **Unit + integration suite:** `./mvnw test` — 66 tests, 0 failures, 0 errors (22 Docker-gated tests skip when no daemon is available).
- **Load test:** 10,000 agents / 20,000 payloads, 0 failures, ~5.7 s, ~1,764 agents/sec (see [10k results](../loadtest/10k-load-test-results.md)).
- **Pinning gate:** JFR-based `VirtualThreadPinningTest` asserts zero `jdk.VirtualThreadPinned` events.
- **Leak gate:** `TaskHistoryLeakTest` verifies bounded task history.
- **CI:** Java 21 + preview APIs, Redis service, branch/commit policy checks, artifact upload.

## Architecture at a glance

```
Agent (REST / gRPC)
        |
        v
AgentPayloadService  --[node=<id>]-->  AgentTaskService (virtual thread)
        |                                   |
        +--> NodeLoadBalancer               +--> StructuredWorkflowService
                |                                  |--> analytics sub-task
                +--> RedisNodeRegistry             +--> profile sub-task
                        |                                 (Resilience4j wrapped)
                        +--> orchestrator:nodes (Redis hash)
        |
        +--> AgentRegistry (memory or Redis)
        +--> Dashboard (snapshot + SSE)
```

## Key technical achievements

1. **No carrier-thread pinning** — all task state transitions use `ReentrantLock`, verified by JFR in CI.
2. **Deterministic least-load routing** — nodes ranked by load %, active tasks and id; stale and full nodes excluded.
3. **Safe distributed locking** — atomic Lua release with owner tokens and TTL expiry; verified between two independent Redis connections.
4. **Failure isolation** — Resilience4j circuit breaker, bulkhead, retry, time limiter and fallback on every outbound call.
5. **Reproducible deployment** — one `docker compose up --build` starts Redis and three orchestrator nodes with health checks.
6. **Automated quality gates** — commit-message signing enforcement, branch policy checks and full test runs on every push/PR.

## Known limitations

- Redis is a single point of failure in the Compose topology (no Sentinel/Cluster HA).
- `StructuredTaskScope` is a Java 21 preview API requiring `--enable-preview`.
- Task history is in-memory only; node restarts lose task state.
- Benchmarks use synthetic I/O waits rather than real third-party APIs.

## How to run the demo

See [deployment guide](../deployment/deployment-guide.md) and [rehearsal guide](../demo/rehearsal-guide.md).

## How to build and test

```bash
./mvnw -B -DskipTests compile
./mvnw -B test
docker compose up --build -d
```

## Conclusion

All 20-day objectives were met. The four members' work is integrated, tested, containerised and documented, ready for evaluation and demonstration.
