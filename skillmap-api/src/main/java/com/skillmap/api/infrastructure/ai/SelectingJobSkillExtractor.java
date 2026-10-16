package com.skillmap.api.infrastructure.ai;

import com.skillmap.api.domain.port.JobSkillExtractor;
import com.skillmap.api.infrastructure.config.GeminiApiKeyProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * El extractor que usa el Analizador en el escritorio. Con key propia de Gemini (variable de
 * entorno, config.properties o Ajustes) se llama a Gemini directo con la cuota del usuario; sin
 * ella, al servicio de IA en la nube. Se decide en cada análisis: una key guardada desde Ajustes
 * se nota sin reiniciar.
 */
@Component
@Primary
public class SelectingJobSkillExtractor implements JobSkillExtractor {

    private final GeminiApiKeyProvider apiKeyProvider;
    private final JobSkillExtractor gemini;
    private final RemoteJobSkillExtractor remote;

    @Autowired
    public SelectingJobSkillExtractor(GeminiApiKeyProvider apiKeyProvider,
                                      GeminiJobSkillExtractor gemini,
                                      RemoteJobSkillExtractor remote) {
        this(apiKeyProvider, (JobSkillExtractor) gemini, remote);
    }

    /** Los tests pasan un fake en lugar de Gemini. */
    SelectingJobSkillExtractor(GeminiApiKeyProvider apiKeyProvider, JobSkillExtractor gemini,
                               RemoteJobSkillExtractor remote) {
        this.apiKeyProvider = apiKeyProvider;
        this.gemini = gemini;
        this.remote = remote;
    }

    @Override
    public List<String> extractSkills(String jobDescription, Collection<String> knownSkills) {
        return selected().extractSkills(jobDescription, knownSkills);
    }

    JobSkillExtractor selected() {
        return apiKeyProvider.getApiKey().isPresent() ? gemini : remote;
    }

    /** El plan gratis de Render se duerme: sin key propia, se despierta en segundo plano al arrancar. */
    @EventListener(ApplicationReadyEvent.class)
    public void wakeUpRemoteService() {
        if (selected() != remote) {
            return;
        }
        Thread thread = new Thread(remote::warmUp, "skillmap-ai-warmup");
        thread.setDaemon(true);
        thread.start();
    }
}
