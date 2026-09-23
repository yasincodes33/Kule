package com.AgentSaasAplication.runner.dto;

import java.util.UUID;

/** Düz metin token SADECE bu yanıtta görünür — DB'de yalnızca hash'i saklanıyor. */
public record RunnerTokenResponse(UUID runnerConnectionId, String token) {}
