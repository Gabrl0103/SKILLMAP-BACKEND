package com.skillmap.api.domain.port;

import com.skillmap.api.domain.model.JobPosting;

import java.util.List;

/**
 * Puerto de salida: una bolsa de empleo externa de la que traer ofertas.
 * Hay un adaptador por fuente en infrastructure/; el dominio no sabe si es
 * una API REST, un feed o un archivo.
 */
public interface JobPostingSource {

    /** Nombre estable de la fuente; se guarda con cada oferta. */
    String name();

    /**
     * @return las ofertas que publica ahora mismo la fuente, aún sin id
     * @throws com.skillmap.api.domain.exception.JobSourceException si la fuente falla
     */
    List<JobPosting> fetch();
}
