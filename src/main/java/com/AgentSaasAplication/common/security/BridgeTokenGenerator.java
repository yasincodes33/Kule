package com.AgentSaasAplication.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

public final class BridgeTokenGenerator {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTE_LENGTH = 32; // 256 bit

    private BridgeTokenGenerator() {}

    public record BridgeToken(String plaintext, String hash) {}

    /** Yeni bir token üretir. plaintext SADECE bu anda döner, bir daha asla geri alınamaz. */
    public static BridgeToken generate() {
        byte[] randomBytes = new byte[TOKEN_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(randomBytes);
        String plaintext = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        return new BridgeToken(plaintext, hash(plaintext));
    }

    /** Doğrulama sırasında gelen plaintext token'ı aynı algoritmayla hash'ler. */
    public static String hash(String plaintext) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(plaintext.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algoritması bulunamadı", e);
        }
    }
}