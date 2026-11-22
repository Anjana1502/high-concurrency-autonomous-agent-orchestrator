package com.zaalima.high_concurrency_agent_orchestrator.concurrency;

/**
 * Result of one structured fan-out/fan-in workflow.
 *
 * @param taskName                  logical workflow name
 * @param analytics                 result of the analytics sub-task
 * @param profile                   result of the user-profile sub-task
 * @param executedOnVirtualThread   true when the scope ran on virtual threads
 * @param durationMs                wall-clock duration of the scope
 */
public record WorkflowResult(
        String taskName,
        String analytics,
        String profile,
        boolean executedOnVirtualThread,
        long durationMs) {
}
