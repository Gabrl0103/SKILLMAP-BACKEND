package com.skillmap.api.presentation.controller;

import com.skillmap.api.application.service.JobAnalyzerService;
import com.skillmap.api.presentation.dto.AnalysisHistoryDetailResponse;
import com.skillmap.api.presentation.dto.AnalysisHistoryItemResponse;
import com.skillmap.api.presentation.dto.AnalyzeJobRequest;
import com.skillmap.api.presentation.dto.JobAnalysisResponse;
import com.skillmap.api.presentation.filter.AuthenticationFilter;
import com.skillmap.api.presentation.mapper.JobAnalysisResponseMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/analyzer")
public class JobAnalyzerController {

    private final JobAnalyzerService analyzerService;

    public JobAnalyzerController(JobAnalyzerService analyzerService) {
        this.analyzerService = analyzerService;
    }

    @PostMapping("/analyze")
    public JobAnalysisResponse analyze(@RequestAttribute(AuthenticationFilter.USER_ID) Long userId,
                                       @RequestBody AnalyzeJobRequest request) {
        return JobAnalysisResponseMapper.toResponse(analyzerService.analyze(userId, request.jobDescription()));
    }

    /** Historial del usuario, más reciente primero y sin el texto de las ofertas. limit: de 1 a 100 (20 si no viene). */
    @GetMapping("/history")
    public List<AnalysisHistoryItemResponse> history(@RequestAttribute(AuthenticationFilter.USER_ID) Long userId,
                                                     @RequestParam(defaultValue = "" + JobAnalyzerService.DEFAULT_HISTORY_LIMIT) int limit) {
        return analyzerService.getHistory(userId, limit).stream().map(JobAnalysisResponseMapper::toItemResponse).toList();
    }

    /** Un análisis guardado, recalculado con el progreso actual. 404 si no existe o es de otro usuario. */
    @GetMapping("/history/{id}")
    public AnalysisHistoryDetailResponse historyEntry(@RequestAttribute(AuthenticationFilter.USER_ID) Long userId,
                                                      @PathVariable Long id) {
        return JobAnalysisResponseMapper.toResponse(analyzerService.getHistoryEntry(userId, id));
    }

    @DeleteMapping("/history/{id}")
    public ResponseEntity<Void> deleteHistoryEntry(@RequestAttribute(AuthenticationFilter.USER_ID) Long userId,
                                                   @PathVariable Long id) {
        analyzerService.deleteHistoryEntry(userId, id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/history")
    public ResponseEntity<Void> clearHistory(@RequestAttribute(AuthenticationFilter.USER_ID) Long userId) {
        analyzerService.clearHistory(userId);
        return ResponseEntity.noContent().build();
    }
}
