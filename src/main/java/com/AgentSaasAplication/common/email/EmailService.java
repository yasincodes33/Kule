package com.AgentSaasAplication.common.email;

import com.AgentSaasAplication.identity.domain.Role;

/**
 * Şifre sıfırlama bağlantısı ve organizasyon davetleri için e-posta gönderim arayüzü
 * (uygulaması: {@link SmtpEmailService}).
 *
 * Gönderim BEST-EFFORT: hiçbir metot checked/unchecked bir istisna fırlatmaz. SMTP geçici olarak
 * erişilemezse (rate limiter/scheduler lock'la AYNI gerekçe) bu, şifre sıfırlama/davet akışının
 * kendisini kırmamalı — DB yazımı zaten tamamlanmış olur, e-posta yalnızca bir bildirim
 * katmanıdır. Hata durumunda yalnızca loglanır.
 */
public interface EmailService {

    void sendPasswordResetEmail(String toEmail, String resetLink);

    void sendInvitationEmail(String toEmail, String organizationName, Role role);
}
