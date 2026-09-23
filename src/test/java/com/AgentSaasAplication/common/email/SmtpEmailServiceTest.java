package com.AgentSaasAplication.common.email;

import com.AgentSaasAplication.identity.domain.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * SMTP gönderiminin doğru içerikle tetiklendiğini ve SMTP erişilemez olduğunda
 * (MailException) şifre sıfırlama/davet akışının kırılmadığını, yani istisnanın dışarı
 * sızmadığını doğrular.
 */
@ExtendWith(MockitoExtension.class)
class SmtpEmailServiceTest {

    @Mock private JavaMailSender mailSender;

    @Test
    void sifre_sifirlama_e_postasi_dogru_alici_ve_baglantiyla_gonderilir() {
        SmtpEmailService svc = new SmtpEmailService(mailSender, "kule@example.com");

        svc.sendPasswordResetEmail("dev@test.com", "http://localhost:5173/reset-password?token=abc123");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage sent = captor.getValue();
        assertThat(sent.getTo()).containsExactly("dev@test.com");
        assertThat(sent.getFrom()).isEqualTo("kule@example.com");
        assertThat(sent.getText()).contains("http://localhost:5173/reset-password?token=abc123");
    }

    @Test
    void davet_e_postasi_organizasyon_adi_ve_rolu_icerir() {
        SmtpEmailService svc = new SmtpEmailService(mailSender, "kule@example.com");

        svc.sendInvitationEmail("aday@test.com", "Acme Corp", Role.DEVELOPER);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage sent = captor.getValue();
        assertThat(sent.getTo()).containsExactly("aday@test.com");
        assertThat(sent.getSubject()).contains("Acme Corp");
        assertThat(sent.getText()).contains("Acme Corp").contains("DEVELOPER");
    }

    @Test
    void smtp_erisilemezse_istisna_disariya_sizmaz() {
        // E-posta best-effort — SMTP'nin geçici erişilemezliği şifre
        // sıfırlama/davet akışının kendisini KIRMAMALI (bkz. EmailService Javadoc).
        SmtpEmailService svc = new SmtpEmailService(mailSender, "kule@example.com");
        doThrow(new MailSendException("bağlantı reddedildi")).when(mailSender).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));

        assertThatCode(() -> svc.sendPasswordResetEmail("dev@test.com", "http://x"))
                .doesNotThrowAnyException();
    }
}
