package com.zaalima.high_concurrency_agent_orchestrator.cluster;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/** Tracks how many tasks are executing on the local node right now. */
@Component
public class NodeTaskTracker {

    private final AtomicLong activeTasks = new AtomicLong();

    public void taskStarted() {
        activeTasks.incrementAndGet();
    }

    public void taskCompleted() {
        activeTasks.updateAndGet(current -> Math.max(0L, current - 1L));
    }

    public long getActiveTasks() {
        return activeTasks.get();
    }
}
