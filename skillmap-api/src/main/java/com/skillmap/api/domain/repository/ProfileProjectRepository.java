package com.skillmap.api.domain.repository;

import com.skillmap.api.domain.model.ProfileProject;

import java.util.List;
import java.util.Optional;

/** Puerto de salida para los proyectos del perfil. Toda consulta va filtrada por usuario. */
public interface ProfileProjectRepository {

    /** En el orden en que se crearon. */
    List<ProfileProject> findByUserId(Long userId);

    Optional<ProfileProject> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);

    /** Crea (id null) o reemplaza el proyecto con sus etiquetas. */
    ProfileProject save(ProfileProject project);

    /** false si no existe o es de otro usuario. */
    boolean deleteByIdAndUserId(Long id, Long userId);

    /** Todos los del usuario, con sus etiquetas (al eliminar su cuenta). */
    void deleteByUserId(Long userId);
}
