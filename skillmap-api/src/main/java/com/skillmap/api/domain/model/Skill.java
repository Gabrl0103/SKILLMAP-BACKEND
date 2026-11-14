package com.skillmap.api.domain.model;

import java.time.Instant;

/**
 * Entidad de dominio. Es un POJO puro: no tiene anotaciones de JPA ni de Spring.
 * La capa de infraestructura es la que sabe cómo persistirla; el dominio
 * solo conoce las reglas de negocio.
 *
 * Dos clases de habilidad:
 * - Del catálogo (ownerId null): globales, con demanda medida en ofertas reales (o la cargada a mano).
 * - Propia de un usuario (ownerId y goalId, ver CustomSkillFactory): la escribe él para uno de sus objetivos
 *   propios, con categoría {@link #CUSTOM_CATEGORY}. No tiene datos de mercado: su demanda es 0 y
 *   {@link #hasDemandData()} es false (nunca se inventa un porcentaje).
 */
public class Skill {

    /** Categoría de las habilidades propias. */
    public static final String CUSTOM_CATEGORY = "Personalizada";

    private final Long id;
    private final String name;
    private final String category;
    private int demandPercentage;
    private DemandSource demandSource;
    private SkillStatus status;
    /** Cuándo pasó el usuario a su estado actual; null si está pendiente o si es la fila global del catálogo. */
    private final Instant statusUpdatedAt;
    /** Dueño de una habilidad propia; null en las del catálogo. */
    private final Long ownerId;
    /** Objetivo propio al que pertenece una habilidad propia; null en las del catálogo. */
    private final Long goalId;

    public Skill(Long id, String name, String category, int demandPercentage, SkillStatus status,
                 DemandSource demandSource) {
        this(id, name, category, demandPercentage, status, demandSource, null, null, null);
    }

    private Skill(Long id, String name, String category, int demandPercentage, SkillStatus status,
                  DemandSource demandSource, Instant statusUpdatedAt, Long ownerId, Long goalId) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.demandPercentage = demandPercentage;
        this.status = status;
        this.demandSource = demandSource;
        this.statusUpdatedAt = statusUpdatedAt;
        this.ownerId = ownerId;
        this.goalId = goalId;
    }

    /** Habilidad propia de un usuario: sin demanda ni origen de demanda. */
    public static Skill custom(Long id, String name, Long ownerId, Long goalId) {
        return new Skill(id, name, CUSTOM_CATEGORY, 0, SkillStatus.PENDING, null, null, ownerId, goalId);
    }

    /** Comportamiento de dominio: marcar como dominada dispara la regla de negocio, no solo cambia un campo. */
    public void markAsMastered() {
        this.status = SkillStatus.MASTERED;
    }

    /** Copia con el estado de un usuario: el catálogo y la demanda son globales, el progreso es de cada uno. */
    public Skill withStatus(SkillStatus status) {
        return new Skill(id, name, category, demandPercentage, status, demandSource, null, ownerId, goalId);
    }

    /** Copia con el progreso de un usuario: su estado y cuándo pasó a él. */
    public Skill withProgress(SkillStatus status, Instant statusUpdatedAt) {
        return new Skill(id, name, category, demandPercentage, status, demandSource, statusUpdatedAt, ownerId, goalId);
    }

    /** Copia de una habilidad propia ligada a otro objetivo (al crearlo, cuando ya tiene id). */
    public Skill withGoal(Long newGoalId) {
        return new Skill(id, name, category, demandPercentage, status, demandSource, statusUpdatedAt, ownerId,
                newGoalId);
    }

    /** La demanda pasa a ser la medida en ofertas reales, y se deja constancia de su origen. */
    public void updateDemandFromJobPostings(int demandPercentage) {
        if (isCustom()) {
            throw new IllegalStateException("Una habilidad propia no tiene datos de mercado");
        }
        if (demandPercentage < 0 || demandPercentage > 100) {
            throw new IllegalArgumentException("El porcentaje de demanda debe estar entre 0 y 100");
        }
        this.demandPercentage = demandPercentage;
        this.demandSource = DemandSource.JOB_POSTINGS;
    }

    /** Habilidad propia de un usuario (no del catálogo). */
    public boolean isCustom() {
        return ownerId != null;
    }

    /** false en las habilidades propias: no hay ofertas con las que medirlas. */
    public boolean hasDemandData() {
        return !isCustom();
    }

    /** Si este usuario puede verla: las del catálogo, todos; las propias, solo su dueño. */
    public boolean isVisibleTo(Long userId) {
        return ownerId == null || ownerId.equals(userId);
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public int getDemandPercentage() { return demandPercentage; }
    /** null en las habilidades propias. */
    public DemandSource getDemandSource() { return demandSource; }
    public SkillStatus getStatus() { return status; }
    public Instant getStatusUpdatedAt() { return statusUpdatedAt; }
    public Long getOwnerId() { return ownerId; }
    public Long getGoalId() { return goalId; }
}
