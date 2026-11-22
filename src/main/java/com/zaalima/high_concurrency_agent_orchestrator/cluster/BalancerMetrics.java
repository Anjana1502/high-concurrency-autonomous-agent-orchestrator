package com.zaalima.high_concurrency_agent_orchestrator.cluster;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** Micrometer metrics exposed through /actuator/metrics and Prometheus. */
@Component
public class BalancerMetrics {

    private static final String ROUTED = "orchestrator.balancer.routed.total";
    private static final String NO_NODE = "orchestrator.balancer.no_node.total";

    private final MeterRegistry registry;
    private final Counter noNodeAvailable;

    public BalancerMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.noNodeAvailable = Counter.builder(NO_NODE)
                .description("Routing attempts where no live node had capacity")
                .register(registry);
    }

    public void recordRoute(String nodeId) {
        registry.counter(ROUTED, "node", nodeId == null ? "none" : nodeId).increment();
    }

    public void recordNoNodeAvailable() {
        noNodeAvailable.increment();
    }
}
