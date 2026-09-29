package com.skillmap.api.application.service;

import com.skillmap.api.domain.model.Skill;

import java.util.List;

/**
 * Puerto de entrada: define los casos de uso disponibles para quien
 * consuma esta capa (en este proyecto, el SkillController).
 */
public interface SkillService {
    List<Skill> getAllSkills();
    Skill getSkillById(Long id);
    Skill markSkillAsMastered(Long id);
}
