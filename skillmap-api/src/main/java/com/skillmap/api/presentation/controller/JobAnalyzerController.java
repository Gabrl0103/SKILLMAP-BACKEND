package com.skillmap.api.presentation.controller;

import com.skillmap.api.application.service.JobAnalyzerService;
import com.skillmap.api.presentation.dto.AnalyzeJobRequest;
import com.skillmap.api.presentation.dto.JobAnalysisResponse;
import com.skillmap.api.presentation.mapper.JobAnalysisResponseMapper;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analyzer")
public class JobAnalyzerController {

    private final JobAnalyzerService analyzerService;

    public JobAnalyzerController(JobAnalyzerService analyzerService) {
        this.analyzerService = analyzerService;
    }

    @PostMapping("/analyze")
    public JobAnalysisResponse analyze(@RequestBody AnalyzeJobRequest request) {
        return JobAnalysisResponseMapper.toResponse(analyzerService.analyze(request.jobDescription()));
    }
}
