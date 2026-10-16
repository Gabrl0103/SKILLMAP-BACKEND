package com.skillmap.api.application.service.impl;

import com.skillmap.api.application.service.JobAnalyzerService;
import com.skillmap.api.domain.model.JobMatch;
import com.skillmap.api.domain.model.Skill;
import com.skillmap.api.domain.port.JobSkillExtractor;
import com.skillmap.api.domain.service.JobDescriptionValidator;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class JobAnalyzerServiceImpl implements JobAnalyzerService {

    static final int MAX_DESCRIPTION_LENGTH = JobDescriptionValidator.MAX_LENGTH;
    private static final double IN_PROGRESS_WEIGHT = 0.5;

    private final JobSkillExtractor skillExtractor;
    private final UserSkillCatalog userSkills;

    public JobAnalyzerServiceImpl(JobSkillExtractor skillExtractor, UserSkillCatalog userSkills) {
        this.skillExtractor = skillExtractor;
        this.userSkills = userSkills;
    }

    @Override
    public JobMatch analyze(Long userId, String jobDescription) {
        JobDescriptionValidator.validate(jobDescription);

        // Índice del catálogo por nombre normalizado para comparar sin distinguir mayúsculas.
        Map<String, Skill> catalog = new LinkedHashMap<>();
        userSkills.findAll(userId).forEach(s -> catalog.putIfAbsent(normalize(s.getName()), s));

        List<String> required = skillExtractor.extractSkills(jobDescription.strip(),
                catalog.values().stream().map(Skill::getName).toList());

        List<String> mastered = new ArrayList<>();
        List<String> inProgress = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (String name : required) {
            String key = normalize(name);
            if (key.isEmpty() || !seen.add(key)) {
                continue;
            }
            Skill skill = catalog.get(key);
            if (skill == null) {
                missing.add(name.strip());
                continue;
            }
            switch (skill.getStatus()) {
                case MASTERED -> mastered.add(skill.getName());
                case IN_PROGRESS -> inProgress.add(skill.getName());
                case PENDING -> missing.add(skill.getName());
            }
        }

        return new JobMatch(matchPercentage(mastered.size(), inProgress.size(), seen.size()),
                mastered, inProgress, missing);
    }

    private static int matchPercentage(int mastered, int inProgress, int total) {
        if (total == 0) {
            return 0;
        }
        return (int) Math.round((mastered + inProgress * IN_PROGRESS_WEIGHT) * 100 / total);
    }

    private static String normalize(String name) {
        return name == null ? "" : name.strip().toLowerCase(Locale.ROOT);
    }
}
