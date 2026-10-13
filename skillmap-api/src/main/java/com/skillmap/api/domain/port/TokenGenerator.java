package com.skillmap.api.domain.port;

/** Tokens de sesión opacos: el token se entrega al cliente y en la base solo se guarda hash(token). */
public interface TokenGenerator {
    String generate();
    String hash(String token);
}
