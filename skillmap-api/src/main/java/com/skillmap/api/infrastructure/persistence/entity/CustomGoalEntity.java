package com.skillmap.api.infrastructure.persistence.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Objetivo propio de un usuario (tabla custom_goals). Tabla nueva: ddl-auto=update la crea en las bases existentes sin
 * tocar las demás, con sus claves foráneas:
 * - user_id → users(id) con ON DELETE CASCADE: al borrar la cuenta se van sus objetivos (y con ellos sus habilidades).
 * - custom_goal_skills.goal_id → custom_goals(id) con ON DELETE CASCADE y skill_id → skills(id).
 * Los ids salen de una secuencia que empieza en 1.000.000: nunca chocan con los de los objetivos base (career_goals,
 * 1–N), así goalId identifica a cualquier objetivo en toda la API (readiness, historial, ofertas, PUT /me/goal).
 * name_key (nombre sin mayúsculas ni espacios de sobra) es único por usuario. Los largos son el doble del máximo en
 * caracteres: un emoji ocupa dos unidades UTF-16.
 */
@Entity
@Table(name = "custom_goals", uniqueConstraints = @UniqueConstraint(
        name = "uk_custom_goals_user_name", columnNames = {"user_id", "name_key"}))
public class CustomGoalEntity {

    public static final long FIRST_ID = 1_000_000L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "custom_goal_ids")
    @SequenceGenerator(name = "custom_goal_ids", sequenceName = "custom_goals_seq", initialValue = 1_000_000,
            allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_custom_goals_user"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private UserEntity user;

    @Column(name = "user_id", nullable = false, insertable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "name_key", nullable = false, length = 80)
    private String nameKey;

    @Column(length = 240)
    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "goal", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("position")
    private List<CustomGoalSkillEntity> skills = new ArrayList<>();

    protected CustomGoalEntity() {
        // Requerido por JPA.
    }

    public CustomGoalEntity(UserEntity user, String name, String nameKey, String description, Instant createdAt) {
        this.user = user;
        this.name = name;
        this.nameKey = nameKey;
        this.description = description;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    /**
     * Nuevos datos; las habilidades quedan en el orden recibido. Las que siguen conservan su fila (solo cambia su
     * posición): si se borraran y se volvieran a crear, Hibernate insertaría antes de borrar y chocaría con el índice
     * único (goal_id, skill_id).
     */
    public void update(String newName, String newNameKey, String newDescription, List<SkillEntity> newSkills,
                       Instant editedAt) {
        this.name = newName;
        this.nameKey = newNameKey;
        this.description = newDescription;
        this.updatedAt = editedAt;
        Map<Long, CustomGoalSkillEntity> kept = new HashMap<>();
        skills.forEach(s -> kept.put(s.getSkillId(), s));
        List<CustomGoalSkillEntity> next = new ArrayList<>();
        for (int i = 0; i < newSkills.size(); i++) {
            CustomGoalSkillEntity row = kept.remove(newSkills.get(i).getId());
            if (row == null) row = new CustomGoalSkillEntity(this, newSkills.get(i), i);
            else row.moveTo(i);
            next.add(row);
        }
        skills.retainAll(next);
        next.stream().filter(row -> !skills.contains(row)).forEach(skills::add);
        skills.sort(Comparator.comparingInt(CustomGoalSkillEntity::getPosition));
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId != null ? userId : user.getId(); }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<CustomGoalSkillEntity> getSkills() { return skills; }
}
