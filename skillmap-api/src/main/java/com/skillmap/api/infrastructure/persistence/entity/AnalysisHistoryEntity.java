package com.skillmap.api.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

// Tabla nueva: ddl-auto=update la crea en las bases existentes sin tocar las demás.
@Entity
@Table(name = "analysis_history", indexes = {
        @Index(name = "idx_analysis_history_user_created", columnList = "user_id, created_at"),
        @Index(name = "idx_analysis_history_user_hash", columnList = "user_id, text_hash")})
public class AnalysisHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 120)
    private String title;

    /** Como mucho JobDescriptionValidator.MAX_LENGTH caracteres. */
    @Column(name = "job_description", nullable = false, length = 8000)
    private String jobDescription;

    /** SHA-256 en hexadecimal del texto normalizado (JobDescriptionText.hash). */
    @Column(name = "text_hash", nullable = false, length = 64)
    private String textHash;

    @Column(nullable = false, length = 16)
    private String engine;

    @Column(name = "match_percentage", nullable = false)
    private int matchPercentage;

    /** {"mastered":[...],"inProgress":[...],"missing":[...]}: las habilidades con su estado en ese momento. */
    @Lob
    @Column(name = "skills_json", nullable = false)
    private String skillsJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AnalysisHistoryEntity() {
        // Requerido por JPA.
    }

    public AnalysisHistoryEntity(Long id, Long userId, String title, String jobDescription, String textHash,
                                 String engine, int matchPercentage, String skillsJson, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.title = title;
        this.jobDescription = jobDescription;
        this.textHash = textHash;
        this.engine = engine;
        this.matchPercentage = matchPercentage;
        this.skillsJson = skillsJson;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getTitle() { return title; }
    public String getJobDescription() { return jobDescription; }
    public String getTextHash() { return textHash; }
    public String getEngine() { return engine; }
    public int getMatchPercentage() { return matchPercentage; }
    public String getSkillsJson() { return skillsJson; }
    public Instant getCreatedAt() { return createdAt; }
}
