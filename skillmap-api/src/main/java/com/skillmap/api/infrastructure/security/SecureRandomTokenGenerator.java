package com.skillmap.api.infrastructure.security;

import com.skillmap.api.domain.port.TokenGenerator;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Token opaco de 32 bytes aleatorios en Base64 URL-safe sin relleno (43 caracteres).
 * En la base se guarda su SHA-256 en hexadecimal (64 caracteres).
 */
@Component
public class SecureRandomTokenGenerator implements TokenGenerator {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom random = new SecureRandom();

    @Override
    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Override
    public String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // Toda JVM trae SHA-256: no debería pasar nunca.
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
