package com.zaalima.high_concurrency_agent_orchestrator.concurrency;

import com.zaalima.high_concurrency_agent_orchestrator.resilience.ExternalServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Member 2 structured concurrency implementation (Java 21 preview API,
 * JEP 453). Two I/O sub-tasks are forked under a single
 * {@link StructuredTaskScope.ShutdownOnFailure}: if either one fails, the
 * scope shuts down the other and the failure is propagated to the caller.
 *
 * <p>Every sub-task is also wrapped in Member 3's Resilience4j chain
 * (circuit breaker + bulkhead + retry + time limiter + fallback) so a slow
 * third-party API cannot cascade into the orchestrator.</p>
 */
@Service
public class StructuredWorkflowService {

    private static final Logger logger = LoggerFactory.getLogger(StructuredWorkflowService.class);

    private final ExternalServiceClient externalServiceClient;

    public StructuredWorkflowService(ExternalServiceClient externalServiceClient) {
        this.externalServiceClient = externalServiceClient;
    }

    public WorkflowResult execute(String taskName) {
        return execute(taskName, Duration.ofSeconds(5));
    }

    public WorkflowResult execute(String taskName, Duration timeout) {
        long startedAt = System.nanoTime();

        try (StructuredTaskScope.ShutdownOnFailure scope =
                     new StructuredTaskScope.ShutdownOnFailure()) {

            StructuredTaskScope.Subtask<String> analytics = scope.fork(
                    () -> guarded("analytics", () -> fetchAnalytics(taskName), "analytics-unavailable"));
            StructuredTaskScope.Subtask<String> profile = scope.fork(
                    () -> guarded("profile", () -> fetchProfile(taskName), "profile-unavailable"));

            scope.joinUntil(Instant.now().plus(timeout));
            scope.throwIfFailed();

            WorkflowResult result = new WorkflowResult(
                    taskName,
                    analytics.get(),
                    profile.get(),
                    Thread.currentThread().isVirtual(),
                    Duration.ofNanos(System.nanoTime() - startedAt).toMillis());

            logger.info("Structured workflow {} completed: {}", taskName, result);
            return result;
        } catch (TimeoutException e) {
            logger.warn("Structured workflow {} timed out after {}", taskName, timeout);
            throw new WorkflowException("Workflow timed out: " + taskName, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WorkflowException("Workflow interrupted: " + taskName, e);
        } catch (ExecutionException e) {
            logger.error("Structured workflow {} failed: {}", taskName, e.getMessage());
            throw new WorkflowException("Workflow failed: " + taskName, e);
        }
    }

    private String guarded(String name, Supplier<String> call, String fallback) {
        return externalServiceClient.call(call, () -> {
            logger.warn("Sub-task {} used fallback: {}", name, fallback);
            return fallback;
        });
    }

    private String fetchAnalytics(String taskName) {
        sleep(150);
        return "analytics(" + taskName + "): events=1280 latency=12ms";
    }

    private String fetchProfile(String taskName) {
        sleep(100);
        return "profile(" + taskName + "): userId=42 region=eu-west-1";
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Sub-task interrupted", e);
        }
    }

    /** Unchecked exception used at the HTTP boundary. */
    public static class WorkflowException extends RuntimeException {
        public WorkflowException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
