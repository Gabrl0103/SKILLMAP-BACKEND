package com.skillmap.api.infrastructure.persistence;

import com.skillmap.api.infrastructure.persistence.entity.AppSettingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio técnico de Spring Data JPA. Solo lo usa SettingsRepositoryImpl. */
public interface AppSettingJpaRepository extends JpaRepository<AppSettingEntity, String> {
}
