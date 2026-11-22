package com.zaalima.high_concurrency_agent_orchestrator.cluster;

/**
 * Current state and resource usage of one orchestrator node.
 *
 * <p>Nodes are stored in the shared Redis hash {@code orchestrator:nodes}.
 * The wire format is pipe-delimited so that the Member 3 dashboard reader
 * ({@code ClusterNodeProvider}) can continue to parse the first two fields
 * while Member 1 adds load metrics:</p>
 *
 * <pre>
 * lastHeartbeatEpochMs|activeTasks|maxConcurrentTasks|cpuLoad|memoryUsagePercent
 * </pre>
 *
 * @param nodeId              unique identifier of the node
 * @param lastHeartbeatEpochMs timestamp of the most recent heartbeat
 * @param activeTasks         tasks currently executing on this node
 * @param maxConcurrentTasks  configured capacity of this node
 * @param cpuLoad             CPU utilisation percentage (0-100)
 * @param memoryUsagePercent  heap utilisation percentage (0-100)
 */
public record NodeInfo(
        String nodeId,
        long lastHeartbeatEpochMs,
        long activeTasks,
        long maxConcurrentTasks,
        double cpuLoad,
        double memoryUsagePercent) {

    public static final int MIN_FIELDS = 2;
    public static final int FIELD_COUNT = 5;

    /** Percentage of configured task capacity currently in use. */
    public double loadPercentage() {
        if (maxConcurrentTasks <= 0) {
            return activeTasks > 0 ? 100.0 : 0.0;
        }
        return Math.min(100.0, (activeTasks * 100.0) / maxConcurrentTasks);
    }

    /** True when the node can still accept another task. */
    public boolean hasAvailableCapacity() {
        return maxConcurrentTasks <= 0 || activeTasks < maxConcurrentTasks;
    }

    /** Serialises to the shared pipe-delimited wire format. */
    public String toWire() {
        return String.join("|",
                String.valueOf(lastHeartbeatEpochMs),
                String.valueOf(activeTasks),
                String.valueOf(maxConcurrentTasks),
                String.valueOf(cpuLoad),
                String.valueOf(memoryUsagePercent));
    }

    /** Parses both the extended format and the legacy two-field format. */
    public static NodeInfo fromWire(String nodeId, String wire) {
        if (wire == null || wire.isBlank()) {
            return null;
        }
        String[] parts = wire.split("\\|", -1);
        if (parts.length < MIN_FIELDS) {
            return null;
        }
        try {
            long heartbeat = Long.parseLong(parts[0].trim());
            long active = Long.parseLong(parts[1].trim());
            long max = parts.length > 2 ? Long.parseLong(parts[2].trim()) : 0L;
            double cpu = parts.length > 3 ? Double.parseDouble(parts[3].trim()) : 0.0;
            double memory = parts.length > 4 ? Double.parseDouble(parts[4].trim()) : 0.0;
            return new NodeInfo(nodeId, heartbeat, active, max, cpu, memory);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
