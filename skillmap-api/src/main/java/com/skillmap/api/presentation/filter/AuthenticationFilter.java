package com.skillmap.api.presentation.filter;

import com.skillmap.api.application.service.AuthService;
import com.skillmap.api.domain.exception.AuthenticationRequiredException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;

/**
 * Todo /api/** exige "Authorization: Bearer &lt;token&gt;" con una sesión vigente, salvo registro,
 * login y /api/health. Deja el id del usuario en el atributo {@link #USER_ID} para los
 * controladores. Va después del filtro CORS (ver WebConfig): un origen no permitido recibe 403
 * antes de llegar aquí, y un 401 a un origen permitido lleva sus cabeceras CORS.
 * Los archivos estáticos del frontend no pasan por aquí.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class AuthenticationFilter extends OncePerRequestFilter {

    public static final String USER_ID = "skillmap.auth.userId";

    private static final Set<String> PUBLIC_PATHS = Set.of("/api/auth/register", "/api/auth/login", "/api/health");
    private static final UrlPathHelper PATH_HELPER = new UrlPathHelper();
    private static final String UNAUTHORIZED_BODY =
            "{\"message\":\"" + AuthenticationRequiredException.MESSAGE + "\"}";

    private final AuthService authService;

    public AuthenticationFilter(AuthService authService) {
        this.authService = authService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (CorsUtils.isPreFlightRequest(request)) {
            return true;
        }
        String path = PATH_HELPER.getPathWithinApplication(request).replaceAll("/{2,}", "/");
        boolean api = path.equals("/api") || path.startsWith("/api/");
        return !api || PUBLIC_PATHS.contains(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<Long> userId = BearerToken.extract(request.getHeader(HttpHeaders.AUTHORIZATION))
                .flatMap(authService::authenticate);
        if (userId.isEmpty()) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(UNAUTHORIZED_BODY);
            return;
        }
        request.setAttribute(USER_ID, userId.get());
        chain.doFilter(request, response);
    }
}
