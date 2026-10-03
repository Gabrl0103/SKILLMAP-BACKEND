package com.skillmap.api.domain.model;

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

    public Skill(Long id, String name, String category, int demandPercentage, SkillStatus status,
                 DemandSource demandSource) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.demandPercentage = demandPercentage;
        this.status = status;
        this.demandSource = demandSource;
    }

    /** Comportamiento de dominio: marcar como dominada dispara la regla de negocio, no solo cambia un campo. */
    public void markAsMastered() {
        this.status = SkillStatus.MASTERED;
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
}
