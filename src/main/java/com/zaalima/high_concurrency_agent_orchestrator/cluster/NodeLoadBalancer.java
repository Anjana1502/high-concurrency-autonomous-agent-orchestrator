package com.zaalima.high_concurrency_agent_orchestrator.cluster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Routes an incoming agent payload to the least-utilised live node.
 *
 * <p>The selected node id is attached to the task name so that Member 2's
 * virtual-thread task executor and the dashboard can show where the work
 * landed. Selection and task execution stay independent: if the cluster view
 * is temporarily empty the payload is still accepted locally.</p>
 */
@Service
@ConditionalOnProperty(name = "orchestrator.registry.mode", havingValue = "redis")
public class NodeLoadBalancer {

    private static final Logger logger = LoggerFactory.getLogger(NodeLoadBalancer.class);

    private final RedisNodeRegistry registry;
    private final BalancerMetrics metrics;

    public NodeLoadBalancer(RedisNodeRegistry registry, BalancerMetrics metrics) {
        this.registry = registry;
        this.metrics = metrics;
    }

    /** Returns the best node id, or empty when no live node has capacity. */
    public Optional<String> selectNodeId() {
        Optional<NodeInfo> selected = registry.selectLeastUtilized();
        if (selected.isEmpty()) {
            metrics.recordNoNodeAvailable();
            logger.warn("Load balancer found no live node with capacity");
            return Optional.empty();
        }
        String nodeId = selected.get().nodeId();
        metrics.recordRoute(nodeId);
        logger.debug("Routed payload to node {} (load={}%, active={})",
                nodeId,
                selected.get().loadPercentage(),
                selected.get().activeTasks());
        return Optional.of(nodeId);
    }

    /** Adds routing information to the task name handed to the executor. */
    public String withRoute(String taskName) {
        String nodeId = selectNodeId().orElse("local-fallback");
        return "[node=" + nodeId + "] " + taskName;
    }
}
