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
    private final int demandPercentage;
    private SkillStatus status;

    public Skill(Long id, String name, String category, int demandPercentage, SkillStatus status) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.demandPercentage = demandPercentage;
        this.status = status;
    }

    /** Comportamiento de dominio: marcar como dominada dispara la regla de negocio, no solo cambia un campo. */
    public void markAsMastered() {
        this.status = SkillStatus.MASTERED;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public int getDemandPercentage() { return demandPercentage; }
    public SkillStatus getStatus() { return status; }
}
