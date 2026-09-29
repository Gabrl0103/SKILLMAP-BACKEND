package com.skillmap.api.application.event;

import com.skillmap.api.domain.event.SkillMasteredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * "Observador" del evento SkillMasteredEvent (patrón Observer, resuelto con
 * el sistema de eventos de Spring en vez de implementarlo a mano).
 * Hoy solo deja un log; más adelante puede recalcular el % de preparación
 * del dashboard sin que SkillServiceImpl tenga que saber nada de eso.
 */
@Component
public class SkillMasteredEventListener {

    private static final Logger log = LoggerFactory.getLogger(SkillMasteredEventListener.class);

    @EventListener
    public void onSkillMastered(SkillMasteredEvent event) {
        log.info("Habilidad dominada: {} (id={}) — pendiente recalcular % de preparación",
                event.getSkillName(), event.getSkillId());
    }
}
