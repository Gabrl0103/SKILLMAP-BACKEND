package com.skillmap.api.presentation.dto;

import java.util.List;

/** Habilidades técnicas que pide la oferta y el motor que las extrajo. */
public record ExtractSkillsResponse(List<String> skills, String engine) {
}
