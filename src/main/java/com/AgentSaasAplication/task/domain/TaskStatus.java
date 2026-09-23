package com.AgentSaasAplication.task.domain;

public enum TaskStatus {
    QUEUED,
    DISPATCHED,
    RUNNING,
    AWAITING_APPROVAL,
    COMPLETED,
    FAILED,
    REJECTED,
    CANCELLED
}