package com.AgentSaasAplication;

import com.AgentSaasAplication.common.domain.TaskType;
import com.AgentSaasAplication.common.exceptions.NotFoundException;
import com.AgentSaasAplication.common.tenant.TenantContext;
import com.AgentSaasAplication.identity.domain.Organization;
import com.AgentSaasAplication.identity.domain.User;
import com.AgentSaasAplication.identity.repository.UserRepository;
import com.AgentSaasAplication.identity.service.OrganizationService;
import com.AgentSaasAplication.project.domain.Project;
import com.AgentSaasAplication.project.service.ProjectService;
import com.AgentSaasAplication.task.domain.Task;
import com.AgentSaasAplication.task.service.TaskOrchestrationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TenantConnectionAspect ile Postgres Row Level Security politikalarının organizasyon
 * sınırlarını gerçekten uyguladığını, kod incelemesiyle değil gerçek bir veritabanına
 * karşı kanıtlar.
 *
 * Servis katmanı (TaskOrchestrationService/ProjectService/OrganizationService) bilinçli
 * olarak kullanılıyor, ham repository çağrıları değil: TenantConnectionAspect yalnızca
 * dışarıdan normal bir bean çağrısıyla ulaşılan @Transactional metotlarda güvenilir
 * tetiklenir, dolayısıyla test edilen yol üretimde işleyen yolun aynısıdır.
 */
@SpringBootTest
@ActiveProfiles("test")  // testler docker-compose veritabanina baglanir (bkz. application-test.properties)
@TestPropertySource(properties = {
        "app.security.api-key-secret=Nob4gLqy5BdHw4+JVQLwPOn/pZPAwRHAbsNLrhT8vrw=",
        "app.jwt.secret=u0jPk8Yj54/bZdstnsXwYA/HSwg07yaSPdXGOh3dURg="
})
class RowLevelSecurityIntegrationTest {

    @Autowired private OrganizationService organizationService;
    @Autowired private ProjectService projectService;
    @Autowired private TaskOrchestrationService taskOrchestrationService;
    @Autowired private UserRepository userRepository;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private UUID newUser() {
        String unique = UUID.randomUUID().toString();
        User user = userRepository.save(User.register(unique + "@rls-test.local", "$2a$12$test", null));
        return user.getId();
    }

    private Organization newOrganization(String name) {
        return organizationService.createOrganization(name + "-" + UUID.randomUUID(), newUser());
    }

    private Project newProject(String name) {
        return projectService.createProject(name, "https://example.com/" + UUID.randomUUID() + ".git", "main");
    }

    @Test
    void baska_organizasyonun_task_i_RLS_ile_hicbir_sekilde_gorunmez() {
        Organization orgA = newOrganization("rls-test-org-a");
        Organization orgB = newOrganization("rls-test-org-b");

        TenantContext.set(orgA.getId());
        Project projectA = newProject("rls-test-project-a");
        Task taskA = taskOrchestrationService.createTask(
                newUser(), projectA.getId(), TaskType.DEV, "org A görevi", null, null, null, null);
        TenantContext.clear();

        // Org B'nin oturumunda org A'nın task'ına doğrudan ID ile erişmeye çalışmak NotFound
        // vermeli — RLS satırı görünmez kılıyor, yetki kontrolü değil "kayıt yok" gibi davranıyor
        // (bilinçli: bir saldırgana "bu ID var ama yetkin yok" bilgisini bile sızdırmamak için).
        TenantContext.set(orgB.getId());
        assertThatThrownBy(() -> taskOrchestrationService.getTask(taskA.getId()))
                .isInstanceOf(NotFoundException.class);
        TenantContext.clear();

        // Aynı ID, doğru org'un oturumunda sorunsuz görünür — RLS'in kendisi hatalı/aşırı
        // kısıtlayıcı değil, yalnızca YANLIŞ org'u engelliyor.
        TenantContext.set(orgA.getId());
        Task found = taskOrchestrationService.getTask(taskA.getId());
        assertThat(found.getId()).isEqualTo(taskA.getId());
    }

    @Test
    void task_listeleme_yalnizca_kendi_organizasyonunun_kayitlarini_donuyor() {
        Organization orgA = newOrganization("rls-list-test-org-a");
        Organization orgB = newOrganization("rls-list-test-org-b");

        TenantContext.set(orgA.getId());
        Project projectA = newProject("rls-list-test-project-a");
        Task taskA = taskOrchestrationService.createTask(
                newUser(), projectA.getId(), TaskType.DEV, "org A görevi", null, null, null, null);
        TenantContext.clear();

        TenantContext.set(orgB.getId());
        Project projectB = newProject("rls-list-test-project-b");
        Task taskB = taskOrchestrationService.createTask(
                newUser(), projectB.getId(), TaskType.DEV, "org B görevi", null, null, null, null);

        var orgBTasks = taskOrchestrationService.listTasks(PageRequest.of(0, 50)).getContent();

        assertThat(orgBTasks).extracting(Task::getId).contains(taskB.getId()).doesNotContain(taskA.getId());
    }
}
