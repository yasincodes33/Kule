package com.AgentSaasAplication.agent.domain;

/**
 * Bulut model sağlayıcıları. Yerel araçlar (Claude Code, Antigravity, Hermes, OpenClaw,
 * OmniRoute) bilinçli olarak burada değil: onlar backend'in bağlandığı sağlayıcılar değil,
 * kullanıcının kendi makinesinde runner üzerinden seçtiği araçlar — bkz. common.domain.UsedAgent.
 */
public enum AgentType {
    CLAUDE,
    CHATGPT,
    GEMINI
}
