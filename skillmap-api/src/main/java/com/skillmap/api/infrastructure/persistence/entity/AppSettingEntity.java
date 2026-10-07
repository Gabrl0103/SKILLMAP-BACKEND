package com.skillmap.api.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Un ajuste como par clave-valor: añadir uno nuevo no cambia el esquema. Para ajustes por
 * usuario bastará con añadir la columna del usuario a la clave.
 */
@Entity
@Table(name = "app_settings")
public class AppSettingEntity {

    @Id
    @Column(name = "setting_key", length = 100)
    private String key;

    @Column(name = "setting_value", length = 500)
    private String value;

    protected AppSettingEntity() {
        // Requerido por JPA.
    }

    public AppSettingEntity(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() { return key; }
    public String getValue() { return value; }
}
