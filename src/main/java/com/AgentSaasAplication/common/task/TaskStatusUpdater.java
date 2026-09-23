package com.AgentSaasAplication.common.task;

import java.util.UUID;

/**
 * task modülüne dairektöre bağımlı olmadan task durumunu güncellemek için kullanılan arayüz
 * (Dependency Inversion — TenantAccessValidator/CurrentUserResolver ile aynı desen).
 * agent.connector paketi bunu kullanıyor, gerçek uygulaması task modülünde.
 */
public interface TaskStatusUpdater {
    void markRunning(UUID taskId, String message);
    void markCompleted(UUID taskId, String message);
    void markFailed(UUID taskId, String message);
    void logInfo(UUID taskId, String message);  
}