# Member 1 — Final Report

Owner: Member 1 (Team Lead / Redis & Cluster Coordination)
Branch: `feature/redis-integration`
Scope: Redis connectivity, Pub/Sub, distributed locking, node registry, load balancing, architecture evaluation.

## Summary

Delivered the distributed coordination layer that turns a single orchestrator instance into a horizontally scalable cluster. Redis is used for three distinct purposes: a key/value store with TTL support, a Pub/Sub bus for node lifecycle events, and a coordination backend for mutually exclusive distributed locks. A shared node registry and a deterministic least-utilised-node selector route every incoming agent payload to the healthiest node in the cluster.

## Deliverables

| Day | Theme | Key artifacts |
|-----|-------|---------------|
| 1–2 | Repository & contribution rules | `CONTRIBUTING.md`, `.githooks/commit-msg`, GitHub Actions skeleton |
| 3–4 | Redis connectivity & K/V | `RedisService` (`set`, `setWithTtl`, `get`, `delete`, `getTtl`) |
| 5–6 | Redis Pub/Sub | `RedisEventPublisher`, `RedisEventSubscriber`, `NodeEventHandler`, `RedisPubSubConfig` |
| 7–8 | Distributed locking | `RedisDistributedLockService` (atomic Lua release, TTL expiry, owner tokens) |
| 9–10 | Node registry schema | `NodeInfo` (pipe-delimited wire format with legacy compatibility) |
| 11–12 | Heartbeats | `ClusterNodeHeartbeat` publishes liveness + load every 5s |
| 13–14 | Load balancer | `LeastUtilizedNodeSelector`, `NodeLoadBalancer`, `RedisNodeRegistry` |
| 15–16 | Routing integration | `AgentPayloadService` attaches `[node=<id>]` to submitted task names |
| 17–18 | Observability | `BalancerMetrics` (Micrometer counters for routed / no-node-available) |
| 19–20 | Evaluation & tests | `LeastUtilizedNodeSelectorTest`, `RedisClusterIntegrationTest`, this report |

## Key design decisions

- **Wire compatibility:** `NodeInfo` uses `lastHeartbeatEpochMs|activeTasks|maxConcurrentTasks|cpuLoad|memoryUsagePercent`. The Member 3 dashboard's `ClusterNodeProvider` only reads the first two fields, so the extended format remains backward compatible.
- **Deterministic selection:** ranking is by load %, then absolute active tasks, then node id. This makes routing reproducible under equal load and avoids random hot-spots.
- **Stale-node filtering:** nodes whose heartbeat is older than `orchestrator.registry.stale-after-seconds` (default 15s) are excluded and can be pruned with `RedisNodeRegistry.pruneStaleNodes()`.
- **Safe lock release:** release is an atomic Lua script that deletes the key only when the owner token matches, preventing a client from releasing a lock that expired and was re-acquired by another instance.
- **Graceful degradation:** if no live node has capacity, the payload is still accepted locally with a `local-fallback` route marker and the `orchestrator.balancer.no_node.total` counter is incremented.

## Verification

- `LeastUtilizedNodeSelectorTest` — unit tests for selection, stale/full filtering, wire-format round-trip and legacy parsing.
- `RedisClusterIntegrationTest` — Testcontainers-based coverage of K/V + TTL, two-instance mutual exclusion, automatic lock expiry, and registry selection/pruning.
- Full suite: `./mvnw test` — 66 tests run, 0 failures (Docker-gated integration tests skip when Docker is unavailable).

## Cross-module integration

- Shares `orchestrator:nodes` and `orchestrator:agents` keys with Member 3's dashboard and registry.
- `NodeTaskTracker` is updated by Member 2's `AgentTaskService` so active-task counts reflect real execution.
- `NodeLoadBalancer` is injected into Member 3's `AgentPayloadService` via `ObjectProvider` so memory-only deployments work without the cluster beans.

## Known limitations / future work

- Selection is greedy per request; a global reservation protocol would prevent brief over-subscription when many requests arrive simultaneously.
- Redis is a single point of failure in the current Compose topology; a Sentinel or Cluster setup would add HA.
- Node load uses process CPU and heap occupancy as advisory signals; real per-node task capacity is the primary metric.

## Retrospective

- **Learned:** Lua scripts are essential for correct lock release; Spring's `ObjectProvider` keeps optional cross-module beans clean.
- **Would change:** define the `NodeInfo` wire format in a shared schema document before implementing readers, to avoid the later compatibility shim.
