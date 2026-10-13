package com.skillmap.api.application.service;

import com.skillmap.api.domain.model.Skill;

import java.util.List;

/**
 * Puerto de entrada: define los casos de uso disponibles para quien
 * consuma esta capa (en este proyecto, el SkillController).
 */
public interface SkillService {
    /** Todas las habilidades, con el estado del usuario. */
    List<Skill> getAllSkills(Long userId);
    Skill getSkillById(Long userId, Long id);
    /** Solo cambia el progreso de ese usuario. */
    Skill markSkillAsMastered(Long userId, Long id);
}
