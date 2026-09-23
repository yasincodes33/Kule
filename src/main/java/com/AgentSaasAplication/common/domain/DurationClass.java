package com.AgentSaasAplication.common.domain;

import java.time.Duration;

/**
 * Bir aracın gerçekçi çalışma süresi sınıfı. Faz 4 öncesinde tüm TOOL_CALL'lar sabit
 * 180 saniyelik bir zaman aşımıyla bekleniyordu; bu hem hızlı araçlarda (read_file)
 * gereksiz uzun bir askıda kalma, hem ağ işlerinde (git_push/git_pull) erken kesilme
 * anlamına geliyordu. Her araç kendi sınıfını taşıyor, connector o sınıfın süresini
 * kullanıyor.
 */
public enum DurationClass {

    /** Yerel, anlık işlemler — dosya okuma, dizin listeleme, salt-okunur git sorguları. */
    FAST(Duration.ofSeconds(30)),

    /** Yerel ama kullanıcı onayı bekleyebilen işlemler — dosya yazma, git add/commit. */
    MEDIUM(Duration.ofSeconds(120)),

    /** Ağ ya da uzun süren yerel işlemler — git push/pull, keyfi kabuk komutu. */
    SLOW(Duration.ofMinutes(10)),

    /** Görevin tamamının bir CLI AI ajanına devredilmesi (Faz 6 — sidecar). */
    AGENT(Duration.ofMinutes(30));

    private final Duration timeout;

    DurationClass(Duration timeout) {
        this.timeout = timeout;
    }

    public Duration timeout() {
        return timeout;
    }
}
