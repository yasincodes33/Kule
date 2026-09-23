package com.AgentSaasAplication.task.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class TaskStateMachine {

    private static final Map<TaskStatus, Set<TaskStatus>> ALLOWED = new EnumMap<>(TaskStatus.class);

    static {
        ALLOWED.put(TaskStatus.QUEUED, EnumSet.of(TaskStatus.DISPATCHED, TaskStatus.CANCELLED, TaskStatus.FAILED));
        ALLOWED.put(TaskStatus.DISPATCHED, EnumSet.of(TaskStatus.RUNNING, TaskStatus.FAILED, TaskStatus.CANCELLED));
        ALLOWED.put(TaskStatus.RUNNING, EnumSet.of(TaskStatus.AWAITING_APPROVAL, TaskStatus.COMPLETED, TaskStatus.FAILED, TaskStatus.CANCELLED));
        ALLOWED.put(TaskStatus.AWAITING_APPROVAL, EnumSet.of(TaskStatus.RUNNING, TaskStatus.REJECTED));
        ALLOWED.put(TaskStatus.FAILED, EnumSet.of(TaskStatus.QUEUED)); // sadece retry
        // Terminal durumlar: COMPLETED, REJECTED, CANCELLED — hiçbir geçiş yok
        ALLOWED.put(TaskStatus.COMPLETED, EnumSet.noneOf(TaskStatus.class));
        ALLOWED.put(TaskStatus.REJECTED, EnumSet.noneOf(TaskStatus.class));
        ALLOWED.put(TaskStatus.CANCELLED, EnumSet.noneOf(TaskStatus.class));
    }

    private TaskStateMachine() {}

    public static boolean canTransition(TaskStatus from, TaskStatus to) {
        return ALLOWED.getOrDefault(from, EnumSet.noneOf(TaskStatus.class)).contains(to);
    }

    public static boolean isTerminal(TaskStatus status) {
        return ALLOWED.getOrDefault(status, EnumSet.noneOf(TaskStatus.class)).isEmpty();
    }
}