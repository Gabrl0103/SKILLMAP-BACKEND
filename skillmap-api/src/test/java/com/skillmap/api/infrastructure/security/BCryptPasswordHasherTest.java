package com.skillmap.api.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BCryptPasswordHasherTest {

    private final BCryptPasswordHasher hasher = new BCryptPasswordHasher();

    @Test
    void hashesWithCost12AndARandomSalt() {
        String first = hasher.hash("Ruta-al-cenit-2026");
        String second = hasher.hash("Ruta-al-cenit-2026");

        assertThat(first).startsWith("$2a$12$").hasSize(60).doesNotContain("Ruta-al-cenit-2026");
        assertThat(second).isNotEqualTo(first); // sal distinta en cada hash
        assertThat(hasher.matches("Ruta-al-cenit-2026", first)).isTrue();
        assertThat(hasher.matches("Ruta-al-cenit-2027", first)).isFalse();
        assertThat(hasher.needsRehash(first)).isFalse();
    }

    @Test
    void oldCost10HashesStillMatchAndAreMarkedForRehash() {
        String legacy = new BCryptPasswordEncoder().encode("clave-antigua"); // costo 10, el de la versión 1.0
        assertThat(legacy).startsWith("$2a$10$");

        assertThat(hasher.matches("clave-antigua", legacy)).isTrue();
        assertThat(hasher.needsRehash(legacy)).isTrue();
    }

    @Test
    void unknownFormatsNeverMatchAndAreMarkedForRehash() {
        for (String stored : new String[]{"clave-en-claro", "{noop}clave", "5f4dcc3b5aa765d61d8327deb882cf99", ""}) {
            assertThat(hasher.matches("clave-en-claro", stored)).isFalse();
            assertThat(hasher.needsRehash(stored)).isTrue();
        }
        assertThat(hasher.matches(null, hasher.hash("x1234567"))).isFalse();
        assertThat(hasher.matches("x1234567", null)).isFalse();
    }

    @Test
    void neverHashesOrComparesMoreThan72Bytes() {
        String hash = hasher.hash("a".repeat(72));
        // Con 73 bytes BCrypt vería lo mismo que con 72: no se acepta como coincidencia.
        assertThat(hasher.matches("a".repeat(73), hash)).isFalse();
        assertThatThrownBy(() -> hasher.hash("ñ".repeat(37))).isInstanceOf(IllegalArgumentException.class);
    }
}
