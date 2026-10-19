package com.skillmap.api.infrastructure.persistence.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

// Tablas nuevas (profile_projects y profile_project_tags): ddl-auto=update las crea en las bases existentes.
// Los largos son el doble del máximo en caracteres: un emoji ocupa dos unidades UTF-16.
@Entity
@Table(name = "profile_projects")
public class ProfileProjectEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 480)
    private String description;

    @Column(length = 2048)
    private String url;

    // Se borran con el proyecto (también al eliminar la cuenta: el borrado pasa por JPA, no por una consulta).
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "profile_project_tags", joinColumns = @JoinColumn(name = "project_id"))
    @OrderColumn(name = "tag_order")
    @Column(name = "tag", nullable = false, length = 60)
    private List<String> tags = new ArrayList<>();

    protected ProfileProjectEntity() {
        // Requerido por JPA.
    }

    public ProfileProjectEntity(Long id, Long userId, String name, String description, String url, List<String> tags) {
        this.id = id;
        this.userId = userId;
        this.name = name;
        this.description = description;
        this.url = url;
        this.tags = new ArrayList<>(tags);
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getUrl() { return url; }
    public List<String> getTags() { return tags; }
}
