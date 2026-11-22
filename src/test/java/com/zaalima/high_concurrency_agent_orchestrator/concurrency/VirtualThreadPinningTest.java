package com.zaalima.high_concurrency_agent_orchestrator.concurrency;

import com.zaalima.high_concurrency_agent_orchestrator.AgentTaskService;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Automated "no thread pinning" gate. JFR emits
 * {@code jdk.VirtualThreadPinned} when a virtual thread blocks while holding a
 * monitor; the orchestrator must produce zero such events.
 */
class VirtualThreadPinningTest {

    @Test
    void virtualTaskExecutionMustNotPinCarrierThreads() throws Exception {
        Assumptions.assumeTrue(FlightRecorder.isAvailable(), "JFR is not available");

        Path dump = Files.createTempFile("orchestrator-pinning", ".jfr");
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
             Recording recording = new Recording()) {

            recording.enable("jdk.VirtualThreadPinned")
                    .withThreshold(Duration.ZERO);
            recording.start();

            AgentTaskService service = new AgentTaskService(executor);
            for (int i = 0; i < 25; i++) {
                service.submitTask("pinning-check-" + i);
            }

            // Tasks sleep for 2s; wait a little longer so all finish and any
            // monitor contention is observed.
            long deadline = System.nanoTime() + Duration.ofSeconds(6).toNanos();
            while (service.hasRunningTasks() && System.nanoTime() < deadline) {
                Thread.sleep(25);
            }

            recording.stop();
            recording.dump(dump);

            long pinnedEvents = RecordingFile.readAllEvents(dump).stream()
                    .filter(event -> "jdk.VirtualThreadPinned".equals(
                            event.getEventType().getName()))
                    .count();

            assertEquals(0L, pinnedEvents,
                    "Virtual threads must not pin carrier threads (ReentrantLock only)");
            assertTrue(service.getAllTasks().stream()
                    .allMatch(task -> "COMPLETED".equals(task.getStatus())));
        } finally {
            Files.deleteIfExists(dump);
        }
    }
}
