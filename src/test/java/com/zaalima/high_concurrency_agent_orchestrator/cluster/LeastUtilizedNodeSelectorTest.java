package com.zaalima.high_concurrency_agent_orchestrator.cluster;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeastUtilizedNodeSelectorTest {

    private static final long NOW = System.currentTimeMillis();

    @Test
    void selectsLeastLoadedLiveNode() {
        NodeInfo busy = node("node-1", 80, 100, NOW);
        NodeInfo idle = node("node-2", 5, 100, NOW);
        NodeInfo medium = node("node-3", 40, 100, NOW);

        NodeInfo selected = LeastUtilizedNodeSelector
                .select(List.of(busy, medium, idle), NOW - 1000)
                .orElseThrow();

        assertEquals("node-2", selected.nodeId());
    }

    @Test
    void ignoresStaleAndFullNodes() {
        NodeInfo stale = node("stale", 0, 100, NOW - 60_000);
        NodeInfo full = node("full", 100, 100, NOW);
        NodeInfo healthy = node("healthy", 10, 100, NOW);

        List<NodeInfo> ranked = LeastUtilizedNodeSelector.rank(
                List.of(stale, full, healthy), NOW - 5000);

        assertEquals(1, ranked.size());
        assertEquals("healthy", ranked.get(0).nodeId());
    }

    @Test
    void returnsEmptyWhenClusterHasNoCapacity() {
        assertTrue(LeastUtilizedNodeSelector
                .select(List.of(node("full", 10, 10, NOW)), NOW - 5000)
                .isEmpty());
    }

    @Test
    void wireFormatRoundTrips() {
        NodeInfo original = new NodeInfo("node-9", NOW, 12, 100, 34.5, 67.8);
        NodeInfo parsed = NodeInfo.fromWire("node-9", original.toWire());
        assertEquals(original, parsed);
    }

    @Test
    void legacyTwoFieldWireFormatIsStillReadable() {
        NodeInfo parsed = NodeInfo.fromWire("node-1", NOW + "|7");
        assertEquals(NOW, parsed.lastHeartbeatEpochMs());
        assertEquals(7L, parsed.activeTasks());
    }

    private static NodeInfo node(String id, long active, long max, long heartbeat) {
        return new NodeInfo(id, heartbeat, active, max, 10.0, 20.0);
    }
}
