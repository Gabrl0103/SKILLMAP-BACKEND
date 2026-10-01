package com.skillmap.api.presentation.mapper;

import com.skillmap.api.domain.model.JobMatch;
import com.skillmap.api.presentation.dto.JobAnalysisResponse;

public final class JobAnalysisResponseMapper {

    private JobAnalysisResponseMapper() {
    }

    public static JobAnalysisResponse toResponse(JobMatch match) {
        return new JobAnalysisResponse(
                match.matchPercentage(),
                match.mastered(),
                match.inProgress(),
                match.missing()
        );
    }
}
