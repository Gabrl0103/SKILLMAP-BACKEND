package com.skillmap.api.presentation.mapper;

import com.skillmap.api.domain.model.JobPosting;
import com.skillmap.api.domain.model.JobStats;
import com.skillmap.api.domain.model.JobSyncReport;
import com.skillmap.api.presentation.dto.JobPostingResponse;
import com.skillmap.api.presentation.dto.JobStatsResponse;
import com.skillmap.api.presentation.dto.JobSyncResponse;

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

    public static JobSyncResponse toResponse(JobSyncReport report) {
        return new JobSyncResponse(
                report.sources().stream()
                        .map(s -> new JobSyncResponse.SourceResult(s.source(), s.status().name(),
                                s.fetched(), s.added(), s.lastSyncAt(), s.message()))
                        .toList(),
                report.totalPostings(),
                report.demandRecalculated()
        );
    }

    public static JobStatsResponse toResponse(JobStats stats) {
        return new JobStatsResponse(
                stats.totalPostings(),
                stats.sources().stream()
                        .map(s -> new JobStatsResponse.SourceStats(s.name(), s.postings(), s.lastSyncAt()))
                        .toList(),
                stats.lastSyncAt()
        );
    }
}
