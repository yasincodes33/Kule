package com.AgentSaasAplication.common.domain;

/**
 * Görevin fiilen hangi araçla çözüldüğü. Runner kaydında araç tipi seçilmiyor —
 * kullanıcı görevi hangi araçla isterse onunla yapıyor ve bitirirken opsiyonel olarak
 * bunu bildiriyor. Yeni bir araç eklenirse hem bu enum hem DB CHECK kısıtı güncellenmeli
 * (bkz. tasks.used_agent — enum genişletirken CHECK unutma kuralı).
 */
public enum UsedAgent {
    CLAUDE_CODE,
    ANTIGRAVITY,
    HERMES,
    OPENCLAW,
    OMNIROUTE,
    /** Kullanıcı görevi bir AI ajanına devretmeden, doğrudan dosya/git araçlarıyla çözdü. */
    MANUAL
}
