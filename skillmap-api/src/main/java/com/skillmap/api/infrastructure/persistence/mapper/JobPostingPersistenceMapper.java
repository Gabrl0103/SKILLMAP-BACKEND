package com.skillmap.api.infrastructure.persistence.mapper;

import com.skillmap.api.domain.model.JobPosting;
import com.skillmap.api.infrastructure.persistence.entity.JobPostingEntity;

/** Traduce entre el modelo de dominio (JobPosting) y el modelo de persistencia (JobPostingEntity). */
public final class JobPostingPersistenceMapper {

    private JobPostingPersistenceMapper() {
    }

    public static JobPosting toDomain(JobPostingEntity entity) {
        return new JobPosting(
                entity.getId(),
                entity.getSource(),
                entity.getExternalId(),
                entity.getTitle(),
                entity.getCompany(),
                entity.getLocation(),
                entity.isRemote(),
                entity.getTags(),
                entity.getUrl(),
                entity.getDescription(),
                entity.getPublishedAt(),
                entity.getFetchedAt()
        );
    }

    /** Recorta los textos al tamaño de su columna: una descripción enorme no debe tumbar la sincronización. */
    public static JobPostingEntity toEntity(JobPosting posting) {
        return new JobPostingEntity(
                posting.id(),
                posting.source(),
                posting.externalId(),
                truncate(posting.title(), JobPostingEntity.TITLE_LENGTH),
                truncate(posting.company(), 255),
                truncate(posting.location(), JobPostingEntity.TEXT_LENGTH),
                posting.remote(),
                truncate(posting.tags(), JobPostingEntity.TEXT_LENGTH),
                truncate(posting.url(), JobPostingEntity.TEXT_LENGTH),
                truncate(posting.description(), JobPostingEntity.DESCRIPTION_LENGTH),
                posting.publishedAt(),
                posting.fetchedAt()
        );
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
