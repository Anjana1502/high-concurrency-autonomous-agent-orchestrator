package com.zaalima.high_concurrency_agent_orchestrator.cluster;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Deterministic least-utilised-node algorithm.
 *
 * <p>A node is eligible when its last heartbeat is newer than the cutoff and
 * it reports available capacity. Eligible nodes are ranked by:</p>
 * <ol>
 *     <li>load percentage (ascending),</li>
 *     <li>absolute active tasks (ascending),</li>
 *     <li>node id (ascending, for stable tie-breaking).</li>
 * </ol>
 */
public final class LeastUtilizedNodeSelector {

    private static final Comparator<NodeInfo> ORDER =
            Comparator.comparingDouble(NodeInfo::loadPercentage)
                    .thenComparingLong(NodeInfo::activeTasks)
                    .thenComparing(NodeInfo::nodeId);

    private LeastUtilizedNodeSelector() {
    }

    public static List<NodeInfo> rank(List<NodeInfo> nodes, long heartbeatCutoffEpochMs) {
        if (nodes == null || nodes.isEmpty()) {
            return List.of();
        }
        return nodes.stream()
                .filter(node -> node.lastHeartbeatEpochMs() >= heartbeatCutoffEpochMs)
                .filter(NodeInfo::hasAvailableCapacity)
                .sorted(ORDER)
                .toList();
    }

    public static Optional<NodeInfo> select(List<NodeInfo> nodes, long heartbeatCutoffEpochMs) {
        return rank(nodes, heartbeatCutoffEpochMs).stream().findFirst();
    }
}
