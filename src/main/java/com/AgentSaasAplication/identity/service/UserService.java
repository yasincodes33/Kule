package com.AgentSaasAplication.identity.service;

import com.AgentSaasAplication.common.exceptions.NotFoundException;
import com.AgentSaasAplication.identity.domain.User;
import com.AgentSaasAplication.identity.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Kendi profilini düzenleme — şifre değişikliği (kimlik doğrulama ile ilgili
 * olduğu için) bilinçli olarak burada değil, AuthenticationService'te. */
@Slf4j
@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Kullanıcıyı id ile getirir. Controller katmanı UserRepository'ye doğrudan erişmez;
     * tüm okumalar bu servis üzerinden gider. */
    public User getById(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> new NotFoundException("Kullanıcı bulunamadı"));
    }

    @Transactional
    public User updateProfile(UUID userId, String displayName) {
        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("Kullanıcı bulunamadı"));
        user.updateDisplayName(displayName);
        log.info("Profil güncellendi: userId={}", userId);
        return user;
    }
}
