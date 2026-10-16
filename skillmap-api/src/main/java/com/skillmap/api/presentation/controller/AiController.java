package com.skillmap.api.presentation.controller;

import com.skillmap.api.domain.exception.AiNotConfiguredException;
import com.skillmap.api.domain.port.JobSkillExtractor;
import com.skillmap.api.domain.service.JobDescriptionValidator;
import com.skillmap.api.infrastructure.config.CloudConfig;
import com.skillmap.api.infrastructure.security.AiDailyLimiter;
import com.skillmap.api.presentation.dto.ExtractSkillsRequest;
import com.skillmap.api.presentation.dto.ExtractSkillsResponse;
import com.skillmap.api.presentation.exception.GlobalExceptionHandler.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Servicio de IA en la nube: extrae las habilidades de una oferta con la key del servidor, para
 * que la app de escritorio no necesite una propia. Público, con cupo diario por IP y global.
 * Primero se valida: una petición inválida no gasta cupo. El texto de la oferta nunca se registra.
 */
@RestController
@RequestMapping("/api/ai")
@Profile(CloudConfig.PROFILE)
public class AiController {

    static final String ENGINE = "gemini";
    private static final Logger log = LoggerFactory.getLogger(AiController.class);

    private final JobSkillExtractor skillExtractor;
    private final AiDailyLimiter limiter;

    public AiController(JobSkillExtractor skillExtractor, AiDailyLimiter limiter) {
        this.skillExtractor = skillExtractor;
        this.limiter = limiter;
    }

    @PostMapping("/extract-skills")
    public ExtractSkillsResponse extractSkills(@RequestBody ExtractSkillsRequest request,
                                               HttpServletRequest httpRequest) {
        String jobDescription = request.jobDescription();
        JobDescriptionValidator.validate(jobDescription);
        // Detrás del proxy de Render, la IP real del cliente (server.forward-headers-strategy=native).
        limiter.acquire(httpRequest.getRemoteAddr());
        List<String> skills = skillExtractor.extractSkills(jobDescription.strip(), List.of());
        return new ExtractSkillsResponse(skills, ENGINE);
    }

    /** El mensaje general habla de Ajustes y config.properties, que no aplican a quien llama a este servicio. */
    @ExceptionHandler(AiNotConfiguredException.class)
    public ResponseEntity<ErrorResponse> handleNotConfigured() {
        log.warn("Petición de IA sin GEMINI_API_KEY definida en el servidor");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse("El servicio de IA no está configurado en el servidor."));
    }
}
