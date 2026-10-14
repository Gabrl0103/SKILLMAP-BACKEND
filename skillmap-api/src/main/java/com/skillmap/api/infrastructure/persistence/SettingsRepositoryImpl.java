package com.skillmap.api.infrastructure.persistence;

import com.skillmap.api.domain.exception.InvalidSettingsException;
import com.skillmap.api.domain.model.AppSettings;
import com.skillmap.api.domain.model.Theme;
import com.skillmap.api.domain.repository.SettingsRepository;
import com.skillmap.api.infrastructure.persistence.entity.AppSettingEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Adaptador del puerto SettingsRepository: una fila de app_settings por ajuste. */
@Repository
public class SettingsRepositoryImpl implements SettingsRepository {

    private static final Logger log = LoggerFactory.getLogger(SettingsRepositoryImpl.class);
    static final String THEME = "theme";
    static final String SHOW_INTRO = "show-intro";
    static final String REDUCE_MOTION = "reduce-motion";

    private final AppSettingJpaRepository jpaRepository;

    public SettingsRepositoryImpl(AppSettingJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public AppSettings load() {
        Map<String, String> stored = jpaRepository.findAll().stream()
                .filter(s -> s.getValue() != null)
                .collect(Collectors.toMap(AppSettingEntity::getKey, AppSettingEntity::getValue));
        AppSettings defaults = AppSettings.DEFAULTS;
        return new AppSettings(
                theme(stored.get(THEME), defaults.theme()),
                bool(stored.get(SHOW_INTRO), defaults.showIntro()),
                bool(stored.get(REDUCE_MOTION), defaults.reduceMotion()));
    }

    @Override
    public void save(AppSettings settings) {
        jpaRepository.saveAll(List.of(
                new AppSettingEntity(THEME, settings.theme().value()),
                new AppSettingEntity(SHOW_INTRO, String.valueOf(settings.showIntro())),
                new AppSettingEntity(REDUCE_MOTION, String.valueOf(settings.reduceMotion()))));
    }

    /** Un valor guardado que no se entiende (ej. editado a mano) no rompe la pantalla: vale el de por defecto. */
    private static Theme theme(String stored, Theme fallback) {
        if (stored == null) {
            return fallback;
        }
        try {
            return Theme.fromValue(stored);
        } catch (InvalidSettingsException e) {
            log.warn("Ajuste '{}' guardado no válido; se usa el valor por defecto", THEME);
            return fallback;
        }
    }

    private static boolean bool(String stored, boolean fallback) {
        if ("true".equalsIgnoreCase(stored)) {
            return true;
        }
        if ("false".equalsIgnoreCase(stored)) {
            return false;
        }
        return fallback;
    }
}
