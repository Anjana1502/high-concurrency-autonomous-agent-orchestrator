package com.zaalima.high_concurrency_agent_orchestrator.agent;

import com.zaalima.high_concurrency_agent_orchestrator.cluster.NodeInfo;
import com.zaalima.high_concurrency_agent_orchestrator.cluster.NodeTaskTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Publishes this node's liveness and load into the shared
 * {@code orchestrator:nodes} hash so any dashboard in the cluster can render
 * all nodes and the Member 1 balancer can select the least utilised one.
 */
@Component
@ConditionalOnProperty(name = "orchestrator.registry.mode", havingValue = "redis")
public class ClusterNodeHeartbeat {

    private static final Logger logger = LoggerFactory.getLogger(ClusterNodeHeartbeat.class);

    static final String NODES_KEY = "orchestrator:nodes";

    private final StringRedisTemplate redis;
    private final AgentRegistry registry;
    private final NodeTaskTracker taskTracker;
    private final String nodeId;
    private final long maxConcurrentTasks;

    public ClusterNodeHeartbeat(
            StringRedisTemplate redis,
            AgentRegistry registry,
            NodeTaskTracker taskTracker,
            @Value("${orchestrator.node-id:node-local}") String nodeId,
            @Value("${orchestrator.node.capacity:10000}") long maxConcurrentTasks) {
        this.redis = redis;
        this.registry = registry;
        this.taskTracker = taskTracker;
        this.nodeId = nodeId;
        this.maxConcurrentTasks = maxConcurrentTasks;
    }

    @Scheduled(fixedDelayString = "${orchestrator.registry.node-heartbeat-ms:5000}")
    public void publish() {
        long agentActiveTasks = registry.findAll().stream()
                .mapToLong(AgentInfo::getActiveTasks)
                .sum();
        long activeTasks = Math.max(agentActiveTasks, taskTracker.getActiveTasks());

        NodeInfo node = new NodeInfo(
                nodeId,
                System.currentTimeMillis(),
                activeTasks,
                maxConcurrentTasks,
                cpuLoad(),
                memoryUsagePercent());

        String value = node.toWire();
        redis.opsForHash().put(NODES_KEY, nodeId, value);
        logger.debug("Published node heartbeat {} -> {}", nodeId, value);
    }

    private double cpuLoad() {
        try {
            var bean = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            if (bean instanceof com.sun.management.OperatingSystemMXBean os) {
                return Math.max(0.0, Math.min(100.0, os.getProcessCpuLoad() * 100.0));
            }
        } catch (RuntimeException ignored) {
            // Metrics are advisory; a failed read must not stop heartbeats.
        }
        return 0.0;
    }

    private double memoryUsagePercent() {
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        return Math.min(100.0, (used * 100.0) / runtime.maxMemory());
    }
}
