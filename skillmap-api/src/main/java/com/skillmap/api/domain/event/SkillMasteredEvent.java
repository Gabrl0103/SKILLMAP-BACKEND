package com.skillmap.api.domain.event;

/**
 * Evento de dominio: "algo importante pasó". No sabe quién lo escucha ni qué
 * hace con la información (esa es la idea del patrón Observer).
 */
public class SkillMasteredEvent {

    private final Long skillId;
    private final String skillName;

    public SkillMasteredEvent(Long skillId, String skillName) {
        this.skillId = skillId;
        this.skillName = skillName;
    }

    public Long getSkillId() { return skillId; }
    public String getSkillName() { return skillName; }
}
