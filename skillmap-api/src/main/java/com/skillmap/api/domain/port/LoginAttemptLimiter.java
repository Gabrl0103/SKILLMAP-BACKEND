package com.skillmap.api.domain.port;

import java.time.Duration;
import java.util.Optional;

/**
 * Frena los intentos de adivinar contraseñas: cuenta los fallos por correo y por IP y bloquea durante un tiempo que
 * crece con cada bloqueo. El correo cuenta exista o no la cuenta (así el bloqueo no revela qué correos existen).
 */
public interface LoginAttemptLimiter {

    /** Tiempo que falta para poder volver a intentarlo con ese correo desde esa IP; vacío si no hay bloqueo. */
    Optional<Duration> blockedFor(String email, String clientIp);

    /** Un intento fallido más para el correo y para la IP. */
    void recordFailure(String email, String clientIp);

    /** Acierto: el correo vuelve a empezar de cero (la IP no: un acierto con otra cuenta no limpia sus fallos). */
    void reset(String email);
}
