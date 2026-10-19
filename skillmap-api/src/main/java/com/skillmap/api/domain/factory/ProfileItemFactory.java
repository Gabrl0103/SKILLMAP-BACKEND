package com.skillmap.api.domain.factory;

import com.skillmap.api.domain.exception.InvalidProfileDataException;
import com.skillmap.api.domain.model.ProfileLink;
import com.skillmap.api.domain.model.ProfileProject;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Crea enlaces y proyectos del perfil válidos, sin espacios de sobra. Los largos se cuentan en
 * caracteres (un emoji cuenta uno). Solo se aceptan URLs http o https con dominio: javascript:,
 * data:, file: y similares se rechazan. Los mensajes nunca repiten lo recibido.
 */
public final class ProfileItemFactory {

    public static final int MAX_LINKS = 8;
    public static final int MAX_PROJECTS = 10;
    public static final int MAX_TAGS = 5;
    public static final int MAX_URL_LENGTH = 2048;

    private static final String LABEL = "La etiqueta del enlace debe tener entre 1 y 40 caracteres.";
    private static final String NAME = "El nombre del proyecto debe tener entre 1 y 60 caracteres.";
    private static final String INVALID_URL =
            "La URL debe empezar por http:// o https:// (por ejemplo, https://mi-portfolio.com).";

    private ProfileItemFactory() {
    }

    public static ProfileLink link(Long id, Long userId, String label, String url) {
        String cleanLabel = required(label, 40, LABEL);
        String cleanUrl = webUrl(url);
        if (cleanUrl == null) {
            throw new InvalidProfileDataException("Escribe la URL del enlace.");
        }
        return new ProfileLink(id, userId, cleanLabel, cleanUrl);
    }

    public static ProfileProject project(Long id, Long userId, String name, String description, String url,
                                         List<String> tags) {
        return new ProfileProject(id, userId, required(name, 60, NAME),
                optional(description, 240, "La descripción del proyecto puede tener como máximo 240 caracteres."),
                webUrl(url), tags(tags));
    }

    private static String required(String value, int max, String message) {
        String text = optional(value, max, message);
        if (text == null) {
            throw new InvalidProfileDataException(message);
        }
        return text;
    }

    /** Sin espacios al inicio ni al final; vacío queda en null. */
    private static String optional(String value, int max, String message) {
        String stripped = value == null ? "" : value.strip();
        if (stripped.codePointCount(0, stripped.length()) > max) {
            throw new InvalidProfileDataException(message);
        }
        return stripped.isEmpty() ? null : stripped;
    }

    /** Vacía queda en null; si no, URL absoluta http o https con dominio. */
    private static String webUrl(String url) {
        String stripped = url == null ? "" : url.strip();
        if (stripped.isEmpty()) {
            return null;
        }
        if (stripped.length() > MAX_URL_LENGTH) {
            throw new InvalidProfileDataException("La URL puede tener como máximo 2048 caracteres.");
        }
        try {
            URI uri = new URI(stripped);
            String scheme = uri.getScheme();
            if (("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) && uri.getHost() != null) {
                return stripped;
            }
        } catch (URISyntaxException e) {
            // Mal formada: mismo mensaje que un esquema no permitido.
        }
        throw new InvalidProfileDataException(INVALID_URL);
    }

    /** Sin vacías ni repetidas, en el orden recibido. */
    private static List<String> tags(List<String> tags) {
        Set<String> clean = new LinkedHashSet<>();
        for (String tag : tags == null ? List.<String>of() : tags) {
            String text = optional(tag, 30, "Cada etiqueta puede tener como máximo 30 caracteres.");
            if (text != null) {
                clean.add(text);
            }
        }
        if (clean.size() > MAX_TAGS) {
            throw new InvalidProfileDataException("Un proyecto puede tener como máximo 5 etiquetas.");
        }
        return new ArrayList<>(clean);
    }
}
