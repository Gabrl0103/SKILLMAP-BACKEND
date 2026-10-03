package com.skillmap.api.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/** Modelo de persistencia (JPA) de una oferta de empleo. */
@Entity
@Table(name = "job_postings",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_job_postings_source_external_id", columnNames = {"source", "external_id"}),
        indexes = @Index(name = "idx_job_postings_published_at", columnList = "published_at"))
public class JobPostingEntity {

    public static final int TITLE_LENGTH = 500;
    public static final int TEXT_LENGTH = 1000;
    public static final int DESCRIPTION_LENGTH = 20_000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source", nullable = false, length = 50)
    private String source;

    @Column(name = "external_id", nullable = false)
    private String externalId;

    @Column(length = TITLE_LENGTH)
    private String title;

    private String company;

    @Column(length = TEXT_LENGTH)
    private String location;

    private boolean remote;

    @Column(length = TEXT_LENGTH)
    private String tags;

    @Column(length = TEXT_LENGTH)
    private String url;

    @Column(length = DESCRIPTION_LENGTH)
    private String description;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    private Instant fetchedAt;

    protected JobPostingEntity() {
        // Requerido por JPA.
    }

    public JobPostingEntity(Long id, String source, String externalId, String title, String company,
                            String location, boolean remote, String tags, String url, String description,
                            Instant publishedAt, Instant fetchedAt) {
        this.id = id;
        this.source = source;
        this.externalId = externalId;
        this.title = title;
        this.company = company;
        this.location = location;
        this.remote = remote;
        this.tags = tags;
        this.url = url;
        this.description = description;
        this.publishedAt = publishedAt;
        this.fetchedAt = fetchedAt;
    }

    public Long getId() { return id; }
    public String getSource() { return source; }
    public String getExternalId() { return externalId; }
    public String getTitle() { return title; }
    public String getCompany() { return company; }
    public String getLocation() { return location; }
    public boolean isRemote() { return remote; }
    public String getTags() { return tags; }
    public String getUrl() { return url; }
    public String getDescription() { return description; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getFetchedAt() { return fetchedAt; }
}
