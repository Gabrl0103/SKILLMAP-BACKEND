package com.skillmap.api.domain.repository;

import com.skillmap.api.domain.model.ProfileLink;

import java.util.List;
import java.util.Optional;

/** Puerto de salida para los enlaces del perfil. Toda consulta va filtrada por usuario. */
public interface ProfileLinkRepository {

    /** En el orden en que se crearon. */
    List<ProfileLink> findByUserId(Long userId);

    Optional<ProfileLink> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);

    /** Crea (id null) o reemplaza el enlace. */
    ProfileLink save(ProfileLink link);

    /** false si no existe o es de otro usuario. */
    boolean deleteByIdAndUserId(Long id, Long userId);

    /** Todos los del usuario (al eliminar su cuenta). */
    void deleteByUserId(Long userId);
}
