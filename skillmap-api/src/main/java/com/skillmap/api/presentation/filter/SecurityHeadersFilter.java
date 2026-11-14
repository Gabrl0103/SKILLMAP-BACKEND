package com.skillmap.api.presentation.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Cabeceras de seguridad en todas las respuestas (la app que sirve el propio backend, en el navegador o en Electron):
 * <ul>
 *   <li>CSP: scripts, estilos, fuentes y llamadas solo del mismo origen. Los estilos admiten 'unsafe-inline' porque
 *       Tailwind (copia local) genera sus estilos en tiempo de ejecución y la interfaz usa style="--i:n" para escalonar
 *       animaciones; los scripts no (index.html no tiene scripts en línea). Nada puede enmarcar la app.</li>
 *   <li>nosniff, X-Frame-Options DENY, sin Referer hacia fuera y sin cámara, micrófono ni ubicación.</li>
 *   <li>/api/**: Cache-Control no-store (datos de la cuenta: no se guardan en cachés).</li>
 * </ul>
 * Va primero para que también las lleven las respuestas que cortan otros filtros (403 de CORS, 401, 413).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    static final String CONTENT_SECURITY_POLICY = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self' 'unsafe-inline'",
            "img-src 'self' data: blob:",
            "font-src 'self' data:",
            "connect-src 'self'",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("Content-Security-Policy", CONTENT_SECURITY_POLICY);
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.equals("/api") || path.startsWith("/api/")) {
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        }
        chain.doFilter(request, response);
    }
}
