package com.skillmap.api.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.LocalDate;

// Tabla nueva: ddl-auto=update la crea en las bases existentes sin tocar las demás.
@Entity
@Table(name = "readiness_snapshots", uniqueConstraints = @UniqueConstraint(
        name = "uk_readiness_snapshots_user_goal_day", columnNames = {"user_id", "goal_id", "captured_on"}))
public class ReadinessSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "goal_id", nullable = false)
    private Long goalId;

    @Column(nullable = false)
    private int percentage;

    @Column(name = "captured_on", nullable = false)
    private LocalDate capturedOn;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    protected ReadinessSnapshotEntity() {
        // Requerido por JPA.
    }

    public ReadinessSnapshotEntity(Long id, Long userId, Long goalId, int percentage, LocalDate capturedOn,
                                   Instant capturedAt) {
        this.id = id;
        this.userId = userId;
        this.goalId = goalId;
        this.percentage = percentage;
        this.capturedOn = capturedOn;
        this.capturedAt = capturedAt;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getGoalId() { return goalId; }
    public int getPercentage() { return percentage; }
    public LocalDate getCapturedOn() { return capturedOn; }
    public Instant getCapturedAt() { return capturedAt; }
}
