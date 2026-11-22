package com.zaalima.high_concurrency_agent_orchestrator.concurrency;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Demo endpoint for the Week 2 structured-concurrency flow. */
@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {

    private final StructuredWorkflowService workflowService;

    public WorkflowController(StructuredWorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public WorkflowResult run(@RequestBody WorkflowRequest request) {
        if (request == null || request.taskName() == null || request.taskName().isBlank()) {
            throw new IllegalArgumentException("taskName must not be blank");
        }
        return workflowService.execute(request.taskName());
    }

    public record WorkflowRequest(String taskName) {
    }
}
