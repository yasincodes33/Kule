package com.AgentSaasAplication.approval.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RiskyToolPolicy tamamen saf mantık (Spring context'e ihtiyaç yok) — Faz 10'un onay
 * kapısının "hangi araç riskli" kararını tek başına verdiği için doğruluğu kritik: burada bir
 * yanlış negatif (riskli bir çağrının onaysız geçmesi), tüm onay katmanını es geçer.
 */
class RiskyToolPolicyTest {

    private final RiskyToolPolicy policy = new RiskyToolPolicy(
            List.of(".github/", ".env", "pom.xml", "application.yml"));

    @Test
    void git_push_her_zaman_riskli() {
        assertThat(policy.requiresApproval("git_push", Map.of())).isTrue();
        assertThat(policy.requiresApproval("git_push", Map.of("force", true))).isTrue();
    }

    @Test
    void run_command_her_zaman_riskli() {
        assertThat(policy.requiresApproval("run_command", Map.of("command", "echo merhaba"))).isTrue();
    }

    @Test
    void salt_okunur_git_araclari_riskli_degil() {
        assertThat(policy.requiresApproval("git_status", Map.of())).isFalse();
        assertThat(policy.requiresApproval("git_diff", Map.of())).isFalse();
        assertThat(policy.requiresApproval("git_log", Map.of())).isFalse();
        assertThat(policy.requiresApproval("read_file", Map.of("path", "README.md"))).isFalse();
    }

    @Test
    void write_file_korumali_bir_path_e_yaziyorsa_riskli() {
        assertThat(policy.requiresApproval("write_file", Map.of("path", ".env"))).isTrue();
        assertThat(policy.requiresApproval("write_file", Map.of("path", "pom.xml"))).isTrue();
        assertThat(policy.requiresApproval("write_file", Map.of("path", ".github/workflows/ci.yml"))).isTrue();
        assertThat(policy.requiresApproval("write_file", Map.of("path", "src/main/resources/application.yml"))).isTrue();
    }

    @Test
    void write_file_korumasiz_bir_path_e_yaziyorsa_riskli_degil() {
        assertThat(policy.requiresApproval("write_file", Map.of("path", "src/main/java/Foo.java"))).isFalse();
        assertThat(policy.requiresApproval("write_file", Map.of("path", "README.md"))).isFalse();
    }

    @Test
    void write_file_path_eksikse_ya_da_bos_ise_riskli_sayilmaz_exception_atmaz() {
        assertThat(policy.requiresApproval("write_file", Map.of())).isFalse();
        assertThat(policy.requiresApproval("write_file", null)).isFalse();

        Map<String, Object> withNullPath = new java.util.HashMap<>();
        withNullPath.put("path", null);
        assertThat(policy.requiresApproval("write_file", withNullPath)).isFalse();
    }

    @Test
    void windows_ters_egik_cizgili_yollar_da_dogru_eslesiyor() {
        assertThat(policy.requiresApproval("write_file", Map.of("path", "src\\main\\resources\\application.yml")))
                .isTrue();
    }

    @Test
    void buyuk_kucuk_harf_farkli_yazilmis_korumali_path_de_riskli() {
        // Windows/macOS case-insensitive dosya sisteminde
        // "Pom.Xml" ile "pom.xml" AYNI dosya — bir ajan (prompt injection ile ya da kazayla)
        // farklı harf büyüklüğüyle path verirse onay kapısı atlanmamalı.
        assertThat(policy.requiresApproval("write_file", Map.of("path", "Pom.Xml"))).isTrue();
        assertThat(policy.requiresApproval("write_file", Map.of("path", "APPLICATION.YML"))).isTrue();
        assertThat(policy.requiresApproval("write_file", Map.of("path", ".ENV"))).isTrue();
        assertThat(policy.requiresApproval("write_file", Map.of("path", ".GitHub/workflows/ci.yml"))).isTrue();
    }

    @Test
    void korumali_liste_bossa_yalnizca_git_push_run_command_riskli() {
        RiskyToolPolicy emptyPolicy = new RiskyToolPolicy(List.of());
        assertThat(emptyPolicy.requiresApproval("write_file", Map.of("path", ".env"))).isFalse();
        assertThat(emptyPolicy.requiresApproval("git_push", Map.of())).isTrue();
    }
}
