package com.skillmap.api.domain.repository;

import com.skillmap.api.domain.model.Skill;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Puerto de salida (Clean/Hexagonal Architecture).
 * El dominio define QUÉ necesita para persistir habilidades, sin saber CÓMO
 * se hace. La implementación real vive en infrastructure/.
 *
 * findAll() es solo el catálogo global: las habilidades propias de los usuarios no salen ahí (así la demanda, el
 * detector de ofertas, el seed y las pistas de la IA nunca ven las de nadie). Las de un usuario: findByOwner.
 */
public interface SkillRepository {
    List<Skill> findAll();
    /** Cualquier habilidad, del catálogo o propia: quien la use debe comprobar Skill.isVisibleTo. */
    Optional<Skill> findById(Long id);
    Skill save(Skill skill);

    /** Las habilidades propias de un usuario, de la más antigua a la más nueva. */
    List<Skill> findByOwner(Long ownerId);
    /** Borra habilidades propias de ese usuario (las de otro dueño no se tocan). */
    void deleteOwned(Long ownerId, Collection<Long> ids);
    void deleteByOwner(Long ownerId);
}
