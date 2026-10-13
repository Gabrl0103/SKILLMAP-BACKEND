package com.skillmap.api.presentation.filter;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lee el token de "Authorization: Bearer &lt;token&gt;". Solo acepta tokens con forma de token (Base64 URL-safe). */
public final class BearerToken {

    private static final Pattern BEARER = Pattern.compile("^Bearer +([A-Za-z0-9_-]{1,128})$",
            Pattern.CASE_INSENSITIVE);

    private BearerToken() {
    }

    public static Optional<String> extract(String authorizationHeader) {
        if (authorizationHeader == null) {
            return Optional.empty();
        }
        Matcher matcher = BEARER.matcher(authorizationHeader.strip());
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }
}
