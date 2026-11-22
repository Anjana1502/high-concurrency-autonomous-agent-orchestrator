package com.zaalima.high_concurrency_agent_orchestrator.concurrency;

import com.zaalima.high_concurrency_agent_orchestrator.AgentTaskService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards against unbounded growth of the retained task map under load. */
class TaskHistoryLeakTest {

    @Test
    void completedTaskHistoryIsBounded() throws Exception {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            AgentTaskService service = new AgentTaskService(executor);
            for (int i = 0; i < 30; i++) {
                service.submitTask("leak-check-" + i);
            }

            long deadline = System.nanoTime() + Duration.ofSeconds(6).toNanos();
            while (service.hasRunningTasks() && System.nanoTime() < deadline) {
                Thread.sleep(25);
            }

            assertTrue(service.taskCount() >= 30);
            int removed = service.pruneCompletedTasks(10);

            assertTrue(removed > 0);
            assertEquals(10, service.taskCount());
            assertTrue(service.getAllTasks().stream()
                    .allMatch(task -> "COMPLETED".equals(task.getStatus())));
        }
    }
}
