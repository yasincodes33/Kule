package com.AgentSaasAplication.common.email;

import com.AgentSaasAplication.identity.domain.Role;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * `JavaMailSender` ile gerçek SMTP gönderimi — yerelde `docker/docker-
 * compose.yml`'deki Mailhog'a (kimlik doğrulamasız, TLS'siz, localhost:1025) gönderiyor, gerçek
 * bir dağıtımda `SMTP_HOST`/`SMTP_PORT`/`SMTP_USERNAME`/`SMTP_PASSWORD`/`SMTP_AUTH`/
 * `SMTP_STARTTLS` env var'larıyla gerçek bir SMTP sağlayıcısına (SendGrid, SES, kurumsal SMTP
 * relay, vb.) yönlendirilir — bkz. application.yml `spring.mail.*`.
 */
@Slf4j
@Service
public class SmtpEmailService implements EmailService {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public SmtpEmailService(JavaMailSender mailSender, @Value("${app.mail.from}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject("Kule — şifre sıfırlama");
        message.setText("""
                Merhaba,

                Kule hesabın için bir şifre sıfırlama isteği alındı. Aşağıdaki bağlantı 30 dakika \
                geçerlidir ve yalnızca bir kez kullanılabilir:

                %s

                Bu isteği sen yapmadıysan bu e-postayı yok sayabilirsin — hesabında hiçbir şey \
                değişmeyecek.

                — Kule
                """.formatted(resetLink));
        send(message, "şifre sıfırlama", toEmail);
    }

    @Override
    public void sendInvitationEmail(String toEmail, String organizationName, Role role) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject("Kule — \"" + organizationName + "\" organizasyonuna davet edildin");
        message.setText("""
                Merhaba,

                "%s" organizasyonuna %s rolüyle davet edildin.

                Daveti görmek ve kabul etmek için bu e-postayla Kule'ye kaydol ya da (zaten bir \
                hesabın varsa) giriş yap — bekleyen davetin orada seni bekliyor olacak.

                — Kule
                """.formatted(organizationName, role));
        send(message, "davet", toEmail);
    }

    // Bilinçli olarak istisna yutuluyor — bkz. EmailService'in Javadoc'u:
    // e-posta best-effort bir bildirim katmanı, SMTP'nin geçici erişilemezliği şifre sıfırlama/
    // davet akışının kendisini KIRMAMALI (DB yazımı zaten tamamlanmış oluyor).
    private void send(SimpleMailMessage message, String kind, String toEmail) {
        try {
            mailSender.send(message);
            log.info("E-posta gönderildi: tür={}, alıcı={}", kind, toEmail);
        } catch (MailException e) {
            log.error("E-posta gönderilemedi (SMTP erişilemez olabilir) — ilgili işlem yine de tamamlandı: tür={}, alıcı={}",
                    kind, toEmail, e);
        }
    }
}
