package com.skillmap.api.application.service.impl;

import com.skillmap.api.application.service.ReadinessHistoryService;
import com.skillmap.api.application.service.SkillService;
import com.skillmap.api.domain.event.SkillMasteredEvent;
import com.skillmap.api.domain.model.Skill;
import com.skillmap.api.domain.model.SkillStatus;
import com.skillmap.api.domain.model.UserSkill;
import com.skillmap.api.domain.repository.UserSkillRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Implementación del caso de uso. Depende únicamente de INTERFACES
 * (UserSkillRepository, ApplicationEventPublisher), nunca de una clase concreta.
 * Eso es Inyección de Dependencias + Principio de Inversión de Dependencias (la "D" de SOLID).
 *
 * Nota: @Service ya hace que Spring administre esta clase como una única
 * instancia compartida (Singleton) dentro del contenedor. No hace falta
 * escribir un Singleton a mano — el framework ya resuelve ese patrón por nosotros.
 */
@Service
public class SkillServiceImpl implements SkillService {

    private final UserSkillCatalog catalog;
    private final UserSkillRepository userSkillRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ReadinessHistoryService readinessHistory;
    private final Clock clock;

    public SkillServiceImpl(UserSkillCatalog catalog, UserSkillRepository userSkillRepository,
                            ApplicationEventPublisher eventPublisher, ReadinessHistoryService readinessHistory,
                            Clock clock) {
        this.catalog = catalog;
        this.userSkillRepository = userSkillRepository;
        this.eventPublisher = eventPublisher;
        this.readinessHistory = readinessHistory;
        this.clock = clock;
    }

    @Override
    public List<Skill> getAllSkills(Long userId) {
        return catalog.findAll(userId);
    }

    @Override
    public Skill getSkillById(Long userId, Long id) {
        return catalog.findById(userId, id);
    }

    @Override
    public Skill markSkillAsMastered(Long userId, Long id) {
        Skill updated = getSkillById(userId, id);
        UserSkill progress = progress(updated, userId, SkillStatus.MASTERED);
        updated.markAsMastered();
        // Solo el progreso de este usuario: la fila global de la habilidad no cambia.
        userSkillRepository.save(progress);
        readinessHistory.recordSkillChange(userId, id);

        // Patrón Observer: publicamos el evento y quien esté interesado (el listener) reacciona.
        // Este servicio no sabe ni le importa quién escucha.
        eventPublisher.publishEvent(new SkillMasteredEvent(updated.getId(), updated.getName()));

        return updated.withProgress(SkillStatus.MASTERED, progress.updatedAt());
    }

    @Override
    public Skill unmarkSkillAsMastered(Long userId, Long id) {
        // Primero se busca: una habilidad que no existe da 404 aunque no haya nada que borrar.
        Skill skill = getSkillById(userId, id);
        // Sin fila de progreso la habilidad está pendiente (ver UserSkillCatalog): borrarla es deshacer el marcado.
        userSkillRepository.delete(userId, id);
        readinessHistory.recordSkillChange(userId, id);
        return skill.withStatus(SkillStatus.PENDING);
    }

    @Override
    public Skill updateSkillStatus(Long userId, Long id, SkillStatus status) {
        return switch (status) {
            case MASTERED -> markSkillAsMastered(userId, id);
            case PENDING -> unmarkSkillAsMastered(userId, id);
            case IN_PROGRESS -> {
                Skill skill = getSkillById(userId, id);
                UserSkill progress = progress(skill, userId, SkillStatus.IN_PROGRESS);
                // Misma fila de progreso que una dominada: guardar otro estado la reemplaza.
                userSkillRepository.save(progress);
                // Dominada -> aprendiendo baja el %; si no cambia, el historial no guarda nada.
                readinessHistory.recordSkillChange(userId, id);
                yield skill.withProgress(SkillStatus.IN_PROGRESS, progress.updatedAt());
            }
        };
    }

    /**
     * Progreso a guardar con la fecha del cambio. Repetir el estado que ya tenía no la mueve: la fecha dice cuándo pasó
     * a ese estado (las rachas de Mi Ruta cuentan avances por semana), no cuándo se volvió a guardar. Al segundo: la
     * respuesta coincide con lo que se relee de la base.
     */
    private UserSkill progress(Skill current, Long userId, SkillStatus status) {
        Instant changedAt = current.getStatus() == status && current.getStatusUpdatedAt() != null
                ? current.getStatusUpdatedAt()
                : clock.instant().truncatedTo(ChronoUnit.SECONDS);
        return new UserSkill(userId, current.getId(), status, changedAt);
    }
}
