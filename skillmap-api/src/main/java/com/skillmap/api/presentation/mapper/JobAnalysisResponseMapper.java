package com.skillmap.api.presentation.mapper;

import com.skillmap.api.domain.model.AnalysisHistory;
import com.skillmap.api.domain.model.AnalysisHistoryDetail;
import com.skillmap.api.domain.model.JobAnalysis;
import com.skillmap.api.domain.model.JobMatch;
import com.skillmap.api.presentation.dto.AnalysisHistoryDetailResponse;
import com.skillmap.api.presentation.dto.AnalysisHistoryItemResponse;
import com.skillmap.api.presentation.dto.JobAnalysisResponse;

public final class JobAnalysisResponseMapper {

    private JobAnalysisResponseMapper() {
    }

    public static JobAnalysisResponse toResponse(JobAnalysis analysis) {
        JobMatch match = analysis.match();
        return new JobAnalysisResponse(
                match.matchPercentage(),
                match.mastered(),
                match.inProgress(),
                match.missing(),
                analysis.engine(),
                analysis.historyId()
        );
    }

    public static AnalysisHistoryItemResponse toItemResponse(AnalysisHistory entry) {
        return new AnalysisHistoryItemResponse(
                entry.id(),
                entry.title(),
                entry.engine(),
                entry.result().matchPercentage(),
                entry.detectedSkills().size(),
                entry.createdAt()
        );
    }

    public static AnalysisHistoryDetailResponse toResponse(AnalysisHistoryDetail detail) {
        AnalysisHistory entry = detail.entry();
        JobMatch current = detail.current();
        return new AnalysisHistoryDetailResponse(
                entry.id(),
                entry.title(),
                entry.jobDescription(),
                entry.engine(),
                entry.createdAt(),
                entry.result().matchPercentage(),
                true,
                current.matchPercentage(),
                current.mastered(),
                current.inProgress(),
                current.missing()
        );
    }
}
