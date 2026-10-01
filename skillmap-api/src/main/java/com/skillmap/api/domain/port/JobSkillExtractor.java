package com.skillmap.api.domain.port;

import java.util.Collection;
import java.util.List;

/**
 * Puerto de salida: el dominio necesita saber qué habilidades técnicas pide
 * una oferta, sin importar si eso lo resuelve una IA, un diccionario o
 * cualquier otro mecanismo. La implementación vive en infrastructure/.
 */
public interface JobSkillExtractor {

    /**
     * @param jobDescription texto de la oferta laboral
     * @param knownSkills    nombres del catálogo propio; sirven de pista para que
     *                       el extractor use exactamente esos nombres cuando coincidan
     * @return nombres canónicos y cortos de las habilidades técnicas, sin duplicados
     */
    List<String> extractSkills(String jobDescription, Collection<String> knownSkills);
}
