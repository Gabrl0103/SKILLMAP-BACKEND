package com.skillmap.api.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Tabla nueva: ddl-auto=update la crea en las bases existentes sin tocar las demás.
// Los largos son el doble del máximo en caracteres: un emoji ocupa dos unidades UTF-16.
@Entity
@Table(name = "profile_links")
public class ProfileLinkEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 80)
    private String label;

    @Column(nullable = false, length = 2048)
    private String url;

    protected ProfileLinkEntity() {
        // Requerido por JPA.
    }

    public ProfileLinkEntity(Long id, Long userId, String label, String url) {
        this.id = id;
        this.userId = userId;
        this.label = label;
        this.url = url;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getLabel() { return label; }
    public String getUrl() { return url; }
}
