# High-Concurrency Autonomous Agent Orchestrator

## Project Overview

The High-Concurrency Autonomous Agent Orchestrator coordinates and executes large numbers of concurrent autonomous agent workflows across a horizontally scalable cluster. Built on Java 21 Virtual Threads and Spring Boot, it explores high-concurrency execution, distributed coordination with Redis, REST/gRPC interfaces, resilience patterns, and performance benchmarking.

## Project Status

**Complete.** All four members' workstreams are integrated on `main`, covered by automated tests and documented for deployment.

## Objectives

- Build a high-concurrency orchestration backend on Java 21.
- Apply Virtual Threads and Structured Concurrency without carrier-thread pinning.
- Implement Redis Pub/Sub, distributed locking and a cluster node registry with load balancing.
- Provide REST and gRPC agent interfaces with registration security.
- Add Resilience4j patterns and a live cluster dashboard.
- Containerise the cluster and automate builds, tests and quality gates.
- Benchmark virtual vs platform threads under concurrent load.

## Technology Stack

Java 21 (Loom preview APIs) · Spring Boot 4 · Maven · Redis · REST · gRPC · Resilience4j · Docker / Docker Compose · GitHub Actions · JUnit 5 / Testcontainers · Micrometer / Prometheus · JFR

## Team Deliverables

| Member | Area | Highlights | Details |
|--------|------|------------|---------|
| 1 | Redis & cluster coordination | K/V + TTL, Pub/Sub, atomic distributed locks, node registry, least-utilised-node balancer, Micrometer metrics | [Final report](docs/final/member-1-report.md) |
| 2 | Core orchestration engine | Virtual-thread executor (no pinning), `StructuredTaskScope` workflows, cancellation/failure handling, bounded task history, benchmark harness | [Final report](docs/final/member-2-report.md) |
| 3 | Interfaces & resilience | REST + gRPC, registration tokens, agent registry, payload flow, simulator, 10k load test, Resilience4j, dashboard | [Final report](docs/final/member-3-report.md) |
| 4 | Infrastructure & testing | Multi-stage Dockerfile, 3-node Compose cluster, CI quality gates, JFR profiling, deployment guide | [Final report](docs/final/member-4-report.md) |

Consolidated outcome: [docs/final/final-report.md](docs/final/final-report.md).

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

## Quick start

### Local development (no Docker)

```
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

- REST: `POST http://localhost:8080/api/agents`
- Dashboard: `http://localhost:8080/dashboard.html`
- Structured workflow demo: `POST http://localhost:8080/api/workflows` with `{"taskName":"order-42"}`
- Benchmark: `GET http://localhost:8080/api/benchmark?tasks=1000&mode=both`

### Full cluster (3 nodes + Redis)

```
docker compose up --build -d
```

| Service | REST | gRPC |
|---------|------|------|
| node-1 | localhost:8080 | localhost:9090 |
| node-2 | localhost:8081 | localhost:9091 |
| node-3 | localhost:8082 | localhost:9092 |

See [docs/deployment/deployment-guide.md](docs/deployment/deployment-guide.md) for full instructions and configuration reference.

## Build and test

```
.\mvnw.cmd -B -DskipTests compile
.\mvnw.cmd -B test
```

- Java 21 and `--enable-preview` are required (already configured in Maven and Docker).
- Docker-gated integration tests skip automatically when no daemon is available.
- 10k load test: `.\mvnw.cmd test "-Dtest=TenThousandAgentLoadTest" "-Dagent.load.test=true"`

## Documentation index

- [Final report](docs/final/final-report.md) — consolidated outcome across all members
- [Architecture](docs/architecture.md) · [Work distribution](docs/work-distribution.md)
- [Deployment guide](docs/deployment/deployment-guide.md) · [Profiling & benchmarking](docs/benchmark/profiling-and-benchmarking.md)
- [REST API contract](docs/api/agent-api-contract.md) · [OpenAPI spec](docs/openapi/agent-api.yaml) · [gRPC service](docs/grpc/gRPC-service-description.md)
- [Registry schema](docs/registry/registry-schema.md) · [Payload flow](docs/payload/payload-submission-flow.md) · [Security notes](docs/security/registration-security-notes.md)
- [Resilience docs](docs/resilience) · [Dashboard](docs/dashboard/dashboard.md) · [Cluster view](docs/dashboard/cluster-view.md)
- [Load test results](docs/loadtest/10k-load-test-results.md) · [Demo rehearsal](docs/demo/rehearsal-guide.md)
- [Error handling](docs/error-handling.md) · [Contribution guidelines](CONTRIBUTING.md)

## Contributing

Each member works on a feature branch and submits a signed-off commit via PR. See [CONTRIBUTING.md](CONTRIBUTING.md) and `.githooks/commit-msg`.
