package com.skillmap.api.infrastructure.security;

import com.skillmap.api.domain.port.PasswordHasher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Adaptador de PasswordHasher con BCrypt (spring-security-crypto, sin Spring Security completo). Costo 12 por
 * defecto (skillmap.auth.bcrypt-cost); la sal va dentro del hash ("$2a$12$" + 22 de sal + 31 de hash).
 *
 * Migración: hasta la versión 1.0 se guardaba BCrypt con costo 10 (el de BCryptPasswordEncoder por defecto). Esos
 * hashes se siguen verificando tal cual (BCrypt lee el costo del propio hash) y {@link #needsRehash} los marca para
 * volver a calcularlos con el costo actual en el siguiente login correcto. Un hash que no es BCrypt no coincide nunca.
 */
@Component
public class BCryptPasswordHasher implements PasswordHasher {

    public static final int DEFAULT_COST = 12;

    /** BCrypt solo usa los primeros 72 bytes: más largo no se acepta ni se compara. */
    private static final int MAX_BYTES = 72;
    private static final Pattern BCRYPT = Pattern.compile("^\\$2[aby]?\\$(\\d\\d)\\$[./0-9A-Za-z]{53}$");

    private final int cost;
    private final BCryptPasswordEncoder encoder;

    public BCryptPasswordHasher() {
        this(DEFAULT_COST);
    }

    @Autowired
    public BCryptPasswordHasher(@Value("${skillmap.auth.bcrypt-cost:" + DEFAULT_COST + "}") int cost) {
        this.cost = cost;
        this.encoder = new BCryptPasswordEncoder(cost);
    }

    @Override
    public String hash(String rawPassword) {
        if (rawPassword == null || tooLong(rawPassword)) {
            throw new IllegalArgumentException("La contraseña supera el máximo de 72 bytes.");
        }
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        if (rawPassword == null || passwordHash == null || tooLong(rawPassword)
                || !BCRYPT.matcher(passwordHash).matches()) {
            return false;
        }
        return encoder.matches(rawPassword, passwordHash);
    }

    @Override
    public boolean needsRehash(String passwordHash) {
        var matcher = passwordHash == null ? null : BCRYPT.matcher(passwordHash);
        if (matcher == null || !matcher.matches()) {
            return true;
        }
        return Integer.parseInt(matcher.group(1)) < cost;
    }

    private static boolean tooLong(String rawPassword) {
        return rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES;
    }
}
