package com.skillmap.api.domain.model;

import java.time.Instant;

/**
 * Entidad de dominio. Es un POJO puro: no tiene anotaciones de JPA ni de Spring.
 * La capa de infraestructura es la que sabe cómo persistirla; el dominio
 * solo conoce las reglas de negocio.
 */
public class Skill {

    private final Long id;
    private final String name;
    private final String category;
    private int demandPercentage;
    private DemandSource demandSource;
    private SkillStatus status;
    /** Cuándo pasó el usuario a su estado actual; null si está pendiente o si es la fila global del catálogo. */
    private final Instant statusUpdatedAt;

    public Skill(Long id, String name, String category, int demandPercentage, SkillStatus status,
                 DemandSource demandSource) {
        this(id, name, category, demandPercentage, status, demandSource, null);
    }

    private Skill(Long id, String name, String category, int demandPercentage, SkillStatus status,
                  DemandSource demandSource, Instant statusUpdatedAt) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.demandPercentage = demandPercentage;
        this.status = status;
        this.demandSource = demandSource;
        this.statusUpdatedAt = statusUpdatedAt;
    }

    /** Comportamiento de dominio: marcar como dominada dispara la regla de negocio, no solo cambia un campo. */
    public void markAsMastered() {
        this.status = SkillStatus.MASTERED;
    }

    /** Copia con el estado de un usuario: el catálogo y la demanda son globales, el progreso es de cada uno. */
    public Skill withStatus(SkillStatus status) {
        return new Skill(id, name, category, demandPercentage, status, demandSource);
    }

    /** Copia con el progreso de un usuario: su estado y cuándo pasó a él. */
    public Skill withProgress(SkillStatus status, Instant statusUpdatedAt) {
        return new Skill(id, name, category, demandPercentage, status, demandSource, statusUpdatedAt);
    }

    /** La demanda pasa a ser la medida en ofertas reales, y se deja constancia de su origen. */
    public void updateDemandFromJobPostings(int demandPercentage) {
        if (demandPercentage < 0 || demandPercentage > 100) {
            throw new IllegalArgumentException("El porcentaje de demanda debe estar entre 0 y 100");
        }
        this.demandPercentage = demandPercentage;
        this.demandSource = DemandSource.JOB_POSTINGS;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public int getDemandPercentage() { return demandPercentage; }
    public DemandSource getDemandSource() { return demandSource; }
    public SkillStatus getStatus() { return status; }
    public Instant getStatusUpdatedAt() { return statusUpdatedAt; }
}
