package com.skillmap.api.application.service;

import com.skillmap.api.domain.model.JobMatch;

public interface JobAnalyzerService {
    JobMatch analyze(String jobDescription);
}
