package com.skillmap.api.presentation.controller;

import com.skillmap.api.application.service.SkillService;
import com.skillmap.api.presentation.dto.SkillResponse;
import com.skillmap.api.presentation.mapper.SkillResponseMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Adaptador de entrada. Este único controlador lo puede consumir el cliente
 * Desktop, el Mobile o el Wearable — todos hablan el mismo JSON por HTTP,
 * cambia solo cómo lo pintan.
 *
 * Depende de la INTERFAZ SkillService, nunca de SkillServiceImpl directamente.
 * Spring resuelve la implementación real por inyección de dependencias.
 */
@RestController
@RequestMapping("/api/skills")
public class SkillController {

    private final SkillService skillService;

    public SkillController(SkillService skillService) {
        this.skillService = skillService;
    }

    @GetMapping
    public List<SkillResponse> getAllSkills() {
        return skillService.getAllSkills().stream()
                .map(SkillResponseMapper::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    public SkillResponse getSkillById(@PathVariable Long id) {
        return SkillResponseMapper.toResponse(skillService.getSkillById(id));
    }

    @PatchMapping("/{id}/master")
    public SkillResponse markAsMastered(@PathVariable Long id) {
        return SkillResponseMapper.toResponse(skillService.markSkillAsMastered(id));
    }
}
