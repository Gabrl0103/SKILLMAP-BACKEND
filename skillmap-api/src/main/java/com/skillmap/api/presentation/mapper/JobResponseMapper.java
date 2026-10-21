package com.skillmap.api.presentation.mapper;

import com.skillmap.api.domain.model.JobPosting;
import com.skillmap.api.domain.model.JobRecommendation;
import com.skillmap.api.domain.model.JobStats;
import com.skillmap.api.domain.model.JobSyncReport;
import com.skillmap.api.domain.model.SourceSyncStatus;
import com.skillmap.api.presentation.dto.JobDetailResponse;
import com.skillmap.api.presentation.dto.JobPostingResponse;
import com.skillmap.api.presentation.dto.JobRecommendationResponse;
import com.skillmap.api.presentation.dto.JobStatsResponse;
import com.skillmap.api.presentation.dto.JobSyncResponse;

import java.util.Map;

public final class JobResponseMapper {

    private JobResponseMapper() {
    }

    public static JobPostingResponse toResponse(JobPosting posting) {
        return new JobPostingResponse(
                posting.id(),
                posting.source(),
                posting.title(),
                posting.company(),
                posting.location(),
                posting.remote(),
                posting.tags(),
                posting.url(),
                posting.publishedAt()
        );
    }

    public static JobDetailResponse toDetailResponse(JobPosting posting) {
        return new JobDetailResponse(
                posting.id(),
                posting.source(),
                posting.title(),
                posting.company(),
                posting.location(),
                posting.remote(),
                posting.url(),
                posting.publishedAt(),
                posting.description()
        );
    }

    public static JobRecommendationResponse toResponse(JobRecommendation recommendation) {
        JobPosting posting = recommendation.posting();
        return new JobRecommendationResponse(
                posting.id(),
                posting.title(),
                posting.company(),
                posting.source(),
                posting.url(),
                posting.publishedAt(),
                recommendation.matchPercentage(),
                recommendation.masteredSkills(),
                recommendation.missingSkills().stream()
                        .map(s -> new JobRecommendationResponse.MissingSkill(s.name(), s.status().name()))
                        .toList()
        );
    }

    public static JobSyncResponse toResponse(JobSyncReport report) {
        return new JobSyncResponse(
                report.sources().stream()
                        .map(s -> new JobSyncResponse.SourceResult(s.source(), s.status().name(),
                                s.status() == SourceSyncStatus.SYNCED, s.fetched(), s.added(), s.lastSyncAt(),
                                s.nextSyncAt(), s.minutesUntilNextSync(), s.message()))
                        .toList(),
                report.totalPostings(),
                report.demandRecalculated()
        );
    }

    /** @param autoSyncDays cada cuántos días se sincroniza sola cada fuente (las que no aparecen, nunca) */
    public static JobStatsResponse toResponse(JobStats stats, Map<String, Integer> autoSyncDays) {
        return new JobStatsResponse(
                stats.totalPostings(),
                stats.sources().stream()
                        .map(s -> new JobStatsResponse.SourceStats(s.name(), s.postings(), s.lastSyncAt(),
                                s.nextSyncAt(), autoSyncDays.get(s.name())))
                        .toList(),
                stats.lastSyncAt()
        );
    }
}
