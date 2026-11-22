package com.zaalima.high_concurrency_agent_orchestrator.concurrency;

import com.zaalima.high_concurrency_agent_orchestrator.resilience.ExternalServiceClient;
import com.zaalima.high_concurrency_agent_orchestrator.resilience.ResilienceProperties;
import com.zaalima.high_concurrency_agent_orchestrator.resilience.ResilienceRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructuredWorkflowServiceTest {

    private final ResilienceProperties properties = new ResilienceProperties(
            50f, 10, 5, 3, Duration.ofSeconds(10),
            50, Duration.ofMillis(500), 2, Duration.ofMillis(50), Duration.ofSeconds(2));
    private final ResilienceRegistry resilience = new ResilienceRegistry(properties);
    private final StructuredWorkflowService service =
            new StructuredWorkflowService(new ExternalServiceClient(resilience));

    @Test
    void forksAndJoinsBothSubtasksUnderOneScope() {
        WorkflowResult result = service.execute("order-42");

        assertEquals("order-42", result.taskName());
        assertTrue(result.analytics().startsWith("analytics(order-42)"));
        assertTrue(result.profile().startsWith("profile(order-42)"));
        assertTrue(result.durationMs() >= 0);
    }

    @Test
    void timeoutFailsFastInsteadOfHanging() {
        assertThrows(StructuredWorkflowService.WorkflowException.class,
                () -> service.execute("too-slow", Duration.ofMillis(5)));
    }

    @Test
    void resilienceRegistryIsWired() {
        assertNotNull(resilience.circuitBreaker());
        assertNotNull(resilience.bulkhead());
        assertFalse(resilience.circuitBreaker().getState().name().isEmpty());
    }
}
