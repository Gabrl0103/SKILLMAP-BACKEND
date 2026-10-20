package com.skillmap.api.domain.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Lo que el historial del Analizador deriva del texto de una oferta. Dominio puro, sin IA.
 * Dos textos que solo difieren en espacios, saltos de línea o en cómo se codifican las tildes son la misma
 * oferta (mismo hash); las mayúsculas sí cuentan ("Go" no es "go").
 */
public final class JobDescriptionText {

    static final int TITLE_MAX_LENGTH = 80;
    /** Cualquier espacio Unicode, también el de no separación que traen las ofertas copiadas de una web. */
    private static final Pattern WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    private JobDescriptionText() {
    }

    public static String normalize(String text) {
        return WHITESPACE.matcher(Normalizer.normalize(text, Normalizer.Form.NFC)).replaceAll(" ").strip();
    }

    /** SHA-256 en hexadecimal del texto normalizado. */
    public static String hash(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalize(text).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no está disponible", e);
        }
    }

    /** La primera línea con texto, recortada a TITLE_MAX_LENGTH caracteres (con "…" si no cabe). */
    public static String title(String text) {
        String firstLine = text.strip().lines().map(String::strip).filter(line -> !line.isEmpty())
                .findFirst().orElse("");
        String line = normalize(firstLine);
        if (line.codePointCount(0, line.length()) <= TITLE_MAX_LENGTH) {
            return line;
        }
        return line.substring(0, line.offsetByCodePoints(0, TITLE_MAX_LENGTH - 1)).stripTrailing() + "…";
    }
}
