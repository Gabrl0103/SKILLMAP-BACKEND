package com.skillmap.api.domain.port;

/** Hash de contraseñas. Ninguna implementación debe registrar la contraseña ni el hash. */
public interface PasswordHasher {

    String hash(String rawPassword);

    /** Comparación en tiempo constante. Un hash con formato desconocido no coincide nunca (sin excepción). */
    boolean matches(String rawPassword, String passwordHash);

    /**
     * true si el hash es de un esquema o un costo anterior al actual: tras un login correcto se vuelve a calcular con
     * la contraseña recién comprobada (así se migran las cuentas existentes sin pedirles nada).
     */
    boolean needsRehash(String passwordHash);
}
