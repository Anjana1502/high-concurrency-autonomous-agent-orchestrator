# Member 4 — Final Report

Owner: Member 4 (Infrastructure, Testing, Benchmarking & Deployment)
Branch: `feature/github-actions-ci`
Scope: Docker/Compose, CI quality gates, integration tests, profiling/benchmarking, deployment documentation.

## Summary

Packaged the orchestrator for reproducible local and clustered deployment, and put automated quality gates in place. A multi-stage Dockerfile produces a small JRE-based image, Docker Compose starts Redis plus three orchestrator nodes with health checks, and GitHub Actions compiles with preview APIs enabled, checks contribution policy files and runs the full test suite. Added JFR profiling guidance, benchmark result collection and a deployment guide so the team can demo and evaluate the system end-to-end.

## Deliverables

| Day | Theme | Key artifacts |
|-----|-------|---------------|
| 1–2 | Branch/commit policy | `CONTRIBUTING.md`, `.githooks/commit-msg`, `setup-hooks.sh` |
| 3–4 | CI workflow | `.github/workflows/ci.yaml` (Java 21, preview, Redis service, tests) |
| 5–6 | Container image | `Dockerfile` (multi-stage, non-root user, `JAVA_OPTS`) |
| 7–8 | Local cluster | `docker-compose.yml` (Redis + 3 nodes, profiles, health checks) |
| 9–10 | Profiles | `application-dev.properties`, `application-docker.properties` |
| 11–12 | Integration tests | `AbstractRedisIT` hardening, two-instance lock coverage |
| 13–14 | Quality gates | branch-rules job, sign-off enforcement in commit hook |
| 15–16 | Profiling | JFR setup in `docs/benchmark/profiling-and-benchmarking.md` |
| 17–18 | Benchmarks | `BenchmarkController` endpoints + result collection guidance |
| 19–20 | Deployment & report | `docs/deployment/deployment-guide.md`, this report |

## Key design decisions

- **Multi-stage image:** build on `eclipse-temurin:21-jdk`, run on `eclipse-temurin:21-jre` as a non-root user with `--enable-preview` and container-aware heap sizing (`MaxRAMPercentage=70`).
- **Three-node Compose cluster:** each node gets a distinct `ORCHESTRATOR_NODE_ID` and host port, shares Redis, and runs with the `docker` profile so the Member 1 balancer sees all three in `orchestrator:nodes`.
- **CI gates:** a dedicated `branch-rules` job verifies that contribution and hook files exist and that the commit hook enforces DCO sign-off; the main build job compiles and runs all tests with preview APIs enabled.
- **Test isolation:** a test-scoped `application.properties` defaults to in-memory registry so plain `@SpringBootTest` tests need no Redis, while `AbstractRedisIT` explicitly enables Redis mode with dynamic container properties.
- **Benchmark collection:** `GET /api/benchmark?tasks=1000&mode=both` returns structured JSON results suitable for capture into a results file.

## Verification

- `./mvnw test` — 66 tests, 0 failures/errors; Docker-gated suites skip cleanly without a daemon.
- `docker compose config` — validates the three-node topology (run on a machine with Docker).
- CI workflow triggers on pushes to `main`, `feature/**`, `fix/**`, `integration/**` and on PRs to `main`/`integration`.

## Cross-module integration

- Consumes Member 1's node registry and Member 2's executor mode via environment variables in Compose.
- Exposes Member 2's benchmark endpoints and Member 3's actuator/dashboard ports through Compose port mappings.
- Enforces contribution rules authored collaboratively at project start.

## Known limitations / future work

- Compose uses a single Redis instance; production should add persistence and HA (Sentinel/Cluster).
- Image tags are unpinned (`7-alpine`, `21-jre`); pinning digests would improve reproducibility.
- CI uploads test reports but does not publish benchmark numbers as artifacts yet; adding a scheduled benchmark job is a natural next step.

## Retrospective

- **Learned:** preview APIs must be enabled consistently in Maven compiler, Surefire, Spring Boot plugin and the container JVM.
- **Would change:** add the test-scoped in-memory configuration on day one so local builds never depend on a running Redis.
