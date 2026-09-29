package com.skillmap.api.application.service.impl;

import com.skillmap.api.application.service.SkillService;
import com.skillmap.api.domain.event.SkillMasteredEvent;
import com.skillmap.api.domain.exception.SkillNotFoundException;
import com.skillmap.api.domain.model.Skill;
import com.skillmap.api.domain.repository.SkillRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Implementación del caso de uso. Depende únicamente de INTERFACES
 * (SkillRepository, ApplicationEventPublisher), nunca de una clase concreta.
 * Eso es Inyección de Dependencias + Principio de Inversión de Dependencias (la "D" de SOLID).
 *
 * Nota: @Service ya hace que Spring administre esta clase como una única
 * instancia compartida (Singleton) dentro del contenedor. No hace falta
 * escribir un Singleton a mano — el framework ya resuelve ese patrón por nosotros.
 */
@Service
public class SkillServiceImpl implements SkillService {

    private final SkillRepository skillRepository;
    private final ApplicationEventPublisher eventPublisher;

    public SkillServiceImpl(SkillRepository skillRepository, ApplicationEventPublisher eventPublisher) {
        this.skillRepository = skillRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public List<Skill> getAllSkills() {
        return skillRepository.findAll();
    }

    @Override
    public Skill getSkillById(Long id) {
        return skillRepository.findById(id)
                .orElseThrow(() -> new SkillNotFoundException(id));
    }

    @Override
    public Skill markSkillAsMastered(Long id) {
        Skill skill = getSkillById(id);
        skill.markAsMastered();
        Skill updated = skillRepository.save(skill);

        // Patrón Observer: publicamos el evento y quien esté interesado (el listener) reacciona.
        // Este servicio no sabe ni le importa quién escucha.
        eventPublisher.publishEvent(new SkillMasteredEvent(updated.getId(), updated.getName()));

        return updated;
    }
}
