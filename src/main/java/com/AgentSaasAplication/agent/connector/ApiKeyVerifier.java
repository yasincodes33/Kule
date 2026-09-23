package com.AgentSaasAplication.agent.connector;

import java.util.List;

public interface ApiKeyVerifier {

    /**
     * @param criticalModelOk defaultModel (her TaskType zincirinde mutlaka bulunan model) çalışıyor mu
     * @param failedModels erişilemeyen tüm modeller (default dahil, raporlama amaçlı)
     * @param detail ilk başarısızlığın insan-okunur nedeni (HTTP durumu / sağlayıcı
     *               mesajı); kullanıcıya "API key doğrulanamadı" derken nedenini de
     *               söyleyebilmek için
     */
    record ModelVerificationResult(boolean criticalModelOk, List<String> failedModels, String detail) {
        /** detail'siz kisa bicim (mevcut cagiranlar/testler icin). */
        public ModelVerificationResult(boolean criticalModelOk, List<String> failedModels) {
            this(criticalModelOk, failedModels, null);
        }
    }

    ModelVerificationResult verifyModels(String plaintextApiKey);
}