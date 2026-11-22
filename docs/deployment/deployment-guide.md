# Deployment Guide

This guide covers local development, a single-node run, and the full three-node cluster used for the final demo.

## Prerequisites

- Java 21 (Temurin recommended) with `--enable-preview` support
- Docker 24+ with the Compose plugin (for the cluster)
- Git (with hooks installed via `./setup-hooks.sh` on Linux/macOS)

## 1. Local development (no Docker)

Uses the `dev` profile and in-memory registry:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

- REST: `http://localhost:8080/api/agents`
- Dashboard: `http://localhost:8080/dashboard.html`
- gRPC: `localhost:9090` (disabled in dev by default)

Or on Windows:

```
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

## 2. Single node with Redis

Start Redis:

```bash
docker run -d --name orchestrator-redis -p 6379:6379 redis:7-alpine
```

Run with the default configuration (`orchestrator.registry.mode=redis`):

```bash
./mvnw spring-boot:run
```

## 3. Full cluster (recommended for final demo)

```bash
docker compose up --build -d
```

This starts:

| Service | REST | gRPC | Node id |
|---------|------|------|---------|
| orchestrator-1 | localhost:8080 | localhost:9090 | node-1 |
| orchestrator-2 | localhost:8081 | localhost:9091 | node-2 |
| orchestrator-3 | localhost:8082 | localhost:9092 | node-3 |
| redis | localhost:6379 | — | — |

Verify:

```bash
docker compose ps
curl http://localhost:8080/actuator/health
docker exec -it orchestrator-redis redis-cli HGETALL orchestrator:nodes
```

Stop and clean up:

```bash
docker compose down -v
```

## Configuration reference

| Property | Default | Purpose |
|----------|---------|---------|
| `orchestrator.node-id` | `node-1` | Unique id published to the cluster registry |
| `orchestrator.node.capacity` | `10000` | Max concurrent tasks reported for load calculation |
| `orchestrator.registry.mode` | `redis` | `memory` for single-node dev, `redis` for cluster |
| `orchestrator.registry.stale-after-seconds` | `15` | Heartbeat age after which a node is ignored |
| `orchestrator.registry.node-heartbeat-ms` | `5000` | Heartbeat publish interval |
| `orchestrator.executor.mode` | `virtual` | `virtual` or `platform` (benchmark baseline) |
| `orchestrator.executor.platform-threads` | `200` | Fixed pool size for `platform` mode |
| `orchestrator.grpc.port` | `9090` | gRPC server port |
| `spring.data.redis.host/port` | `localhost/6379` | Redis connection |

Environment variables use Spring Boot relaxed binding, e.g. `ORCHESTRATOR_NODE_ID=node-2`.

## Build and test

```bash
./mvnw -B -DskipTests compile
./mvnw -B test
./mvnw -B verify
```

Note: `--enable-preview` is already configured in Maven (compiler, Surefire, Spring Boot plugin) and in the container `JAVA_OPTS`; no extra flags are needed.

## Troubleshooting

- **Context fails with `Unable to connect to Redis`:** run with `-Dspring-boot.run.profiles=dev` or start Redis. Tests default to in-memory mode via `src/test/resources/application.properties`.
- **`Preview feature` errors:** ensure you are on Java 21 and have not overridden `JAVA_OPTS`/`MAVEN_OPTS` without `--enable-preview`.
- **Port conflicts:** change the host-side ports in `docker-compose.yml` (the `host:container` mapping left side).
- **Stale nodes in dashboard:** wait for the heartbeat interval (5 s) or prune with `RedisNodeRegistry.pruneStaleNodes()`.
