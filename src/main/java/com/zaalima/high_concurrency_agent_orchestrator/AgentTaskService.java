package com.zaalima.high_concurrency_agent_orchestrator;

import com.zaalima.high_concurrency_agent_orchestrator.cluster.NodeTaskTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Member 2 core executor: one virtual thread per agent task.
 *
 * <p>All state transitions use {@link ReentrantLock} instead of
 * {@code synchronized}. A virtual thread that blocks on a monitor can pin its
 * carrier thread; {@code ReentrantLock} parks instead, which keeps the
 * scheduler free to run other virtual threads.</p>
 */
@Service
public class AgentTaskService {

    private static final Logger logger = LoggerFactory.getLogger(AgentTaskService.class);

    private static final List<String> TERMINAL_STATES =
            List.of("COMPLETED", "FAILED", "CANCELLED");

    private final ExecutorService virtualThreadExecutor;
    private final NodeTaskTracker nodeTaskTracker;

    private final ConcurrentMap<String, Future<?>> runningTasks = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AgentTask> tasks = new ConcurrentHashMap<>();

    /** Replaces synchronized blocks to avoid virtual-thread pinning. */
    private final ReentrantLock taskLock = new ReentrantLock();

    @Autowired
    public AgentTaskService(ExecutorService virtualThreadExecutor, NodeTaskTracker nodeTaskTracker) {
        this.virtualThreadExecutor = virtualThreadExecutor;
        this.nodeTaskTracker = nodeTaskTracker;
    }

    /** Constructor used by unit tests that do not need cluster load tracking. */
    public AgentTaskService(ExecutorService virtualThreadExecutor) {
        this(virtualThreadExecutor, null);
    }

    public AgentTask submitTask(String taskName) {
        String taskId = UUID.randomUUID().toString();
        AgentTask task = new AgentTask(taskId, taskName, "RUNNING", null);
        tasks.put(taskId, task);

        try {
            FutureTask<Void> future = new FutureTask<>(() -> {
                try {
                    logger.info("Executing task {} on virtual thread: {}",
                            taskName, Thread.currentThread().isVirtual());

                    // Simulated I/O-bound agent operation.
                    Thread.sleep(2000);

                    taskLock.lock();
                    try {
                        task.transition("RUNNING", "COMPLETED", "Task completed successfully");
                    } finally {
                        taskLock.unlock();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    taskLock.lock();
                    try {
                        task.transition("RUNNING", "FAILED", "Task interrupted");
                    } finally {
                        taskLock.unlock();
                    }
                } catch (Exception e) {
                    taskLock.lock();
                    try {
                        task.transition("RUNNING", "FAILED", "Task execution failed: " + e.getMessage());
                    } finally {
                        taskLock.unlock();
                    }
                    logger.error("Task execution failed. Task ID: {}", taskId, e);
                }
                return null;
            });

            runningTasks.put(taskId, future);

            virtualThreadExecutor.execute(() -> {
                if (nodeTaskTracker != null) {
                    nodeTaskTracker.taskStarted();
                }
                try {
                    future.run();
                } finally {
                    if (nodeTaskTracker != null) {
                        nodeTaskTracker.taskCompleted();
                    }
                    runningTasks.remove(taskId, future);
                }
            });
        } catch (RuntimeException e) {
            runningTasks.remove(taskId);
            taskLock.lock();
            try {
                task.transition("RUNNING", "FAILED", "Task could not be submitted");
            } finally {
                taskLock.unlock();
            }
            logger.error("Task submission failed. Task ID: {}", taskId, e);
        }

        return task;
    }

    public boolean cancelTask(String taskId) {
        AgentTask task = tasks.get(taskId);
        if (task == null) {
            logger.warn("Cannot cancel task because it was not found: {}", taskId);
            return false;
        }

        taskLock.lock();
        try {
            Future<?> future = runningTasks.get(taskId);
            if (future == null || !"RUNNING".equals(task.getStatus())) {
                return false;
            }
            if (!future.cancel(true)) {
                return false;
            }
            boolean cancelled = task.transition(
                    "RUNNING", "CANCELLED", "Task cancelled by request");
            if (!cancelled) {
                logger.warn("Could not update task status after cancellation: {}", taskId);
            }
            return cancelled;
        } finally {
            taskLock.unlock();
        }
    }

    public AgentTask getTask(String taskId) {
        return tasks.get(taskId);
    }

    public List<AgentTask> getAllTasks() {
        return List.copyOf(tasks.values());
    }

    /** Number of retained task records (used by the memory-leak test). */
    public int taskCount() {
        return tasks.size();
    }

    /**
     * Bounds retained task history so sustained load cannot grow the heap
     * without limit. Only terminal tasks are eligible for removal.
     */
    public int pruneCompletedTasks(int maxRetainedTasks) {
        if (maxRetainedTasks < 1 || tasks.size() <= maxRetainedTasks) {
            return 0;
        }
        int removed = 0;
        taskLock.lock();
        try {
            for (String id : List.copyOf(tasks.keySet())) {
                if (tasks.size() <= maxRetainedTasks) {
                    break;
                }
                AgentTask task = tasks.get(id);
                if (task != null && TERMINAL_STATES.contains(task.getStatus())
                        && !runningTasks.containsKey(id)) {
                    if (tasks.remove(id, task)) {
                        removed++;
                    }
                }
            }
        } finally {
            taskLock.unlock();
        }
        return removed;
    }

    /** True when no task is still executing (test and shutdown helper). */
    public boolean hasRunningTasks() {
        return !runningTasks.isEmpty();
    }
}
