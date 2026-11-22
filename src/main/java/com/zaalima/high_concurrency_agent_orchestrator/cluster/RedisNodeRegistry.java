package com.zaalima.high_concurrency_agent_orchestrator.cluster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Member 1 node registry. Every orchestrator instance publishes a heartbeat
 * into the shared {@code orchestrator:nodes} hash; readers select the least
 * utilised live node for incoming agent payloads.
 */
@Service
@ConditionalOnProperty(name = "orchestrator.registry.mode", havingValue = "redis")
public class RedisNodeRegistry {

    private static final Logger logger = LoggerFactory.getLogger(RedisNodeRegistry.class);

    public static final String NODES_KEY = "orchestrator:nodes";

    private final StringRedisTemplate redis;
    private final Duration staleAfter;

    public RedisNodeRegistry(
            StringRedisTemplate redis,
            @Value("${orchestrator.registry.stale-after-seconds:15}") long staleAfterSeconds) {
        this.redis = redis;
        this.staleAfter = Duration.ofSeconds(staleAfterSeconds);
    }

    public void save(NodeInfo nodeInfo) {
        if (nodeInfo == null || nodeInfo.nodeId() == null || nodeInfo.nodeId().isBlank()) {
            throw new IllegalArgumentException("nodeId is required");
        }
        redis.opsForHash().put(NODES_KEY, nodeInfo.nodeId(), nodeInfo.toWire());
        logger.debug("Saved node {} -> {}", nodeInfo.nodeId(), nodeInfo.toWire());
    }

    public Optional<NodeInfo> find(String nodeId) {
        Object raw = redis.opsForHash().get(NODES_KEY, nodeId);
        return raw == null ? Optional.empty() : Optional.ofNullable(NodeInfo.fromWire(nodeId, raw.toString()));
    }

    public List<NodeInfo> findAll() {
        Map<Object, Object> entries = redis.opsForHash().entries(NODES_KEY);
        List<NodeInfo> nodes = new ArrayList<>(entries.size());
        entries.forEach((key, value) -> {
            NodeInfo node = NodeInfo.fromWire(String.valueOf(key), String.valueOf(value));
            if (node != null) {
                nodes.add(node);
            }
        });
        return List.copyOf(nodes);
    }

    public boolean remove(String nodeId) {
        Long removed = redis.opsForHash().delete(NODES_KEY, nodeId);
        return removed != null && removed > 0;
    }

    /** Removes heartbeat entries that have not been refreshed in time. */
    public int pruneStaleNodes() {
        long cutoff = System.currentTimeMillis() - staleAfter.toMillis();
        int removed = 0;
        for (NodeInfo node : findAll()) {
            if (node.lastHeartbeatEpochMs() < cutoff) {
                removed += remove(node.nodeId()) ? 1 : 0;
            }
        }
        return removed;
    }

    /** Picks the least utilised live node, or empty when none is available. */
    public Optional<NodeInfo> selectLeastUtilized() {
        long cutoff = System.currentTimeMillis() - staleAfter.toMillis();
        return LeastUtilizedNodeSelector.select(findAll(), cutoff);
    }

    /** Returns the least loaded node sorted by load, then by active task count. */
    public List<NodeInfo> rankedNodes() {
        long cutoff = System.currentTimeMillis() - staleAfter.toMillis();
        return LeastUtilizedNodeSelector.rank(findAll(), cutoff);
    }
}
