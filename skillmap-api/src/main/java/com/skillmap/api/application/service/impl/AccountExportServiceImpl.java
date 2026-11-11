package com.skillmap.api.application.service.impl;

import com.skillmap.api.application.service.AccountExportService;
import com.skillmap.api.domain.exception.AuthenticationRequiredException;
import com.skillmap.api.domain.model.AccountExport;
import com.skillmap.api.domain.model.CareerGoal;
import com.skillmap.api.domain.model.ReadinessSnapshot;
import com.skillmap.api.domain.model.User;
import com.skillmap.api.domain.repository.AnalysisHistoryRepository;
import com.skillmap.api.domain.repository.ProfileLinkRepository;
import com.skillmap.api.domain.repository.ProfileProjectRepository;
import com.skillmap.api.domain.repository.ReadinessSnapshotRepository;
import com.skillmap.api.domain.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Lee la cuenta, su catálogo de habilidades con el estado del usuario, enlaces, proyectos, análisis guardados (todos:
 * el historial ya se recorta al guardar) y el historial de preparación de cada objetivo. Solo lectura.
 */
@Service
public class AccountExportServiceImpl implements AccountExportService {

    /** Más que el tope que conserva el Analizador: así entran todos los análisis guardados. */
    static final int MAX_ANALYSES = 1000;
    private static final LocalDate SINCE_ALWAYS = LocalDate.of(2000, 1, 1);

    private final UserRepository userRepository;
    private final GoalCatalog goalCatalog;
    private final UserSkillCatalog catalog;
    private final ProfileLinkRepository profileLinkRepository;
    private final ProfileProjectRepository profileProjectRepository;
    private final AnalysisHistoryRepository analysisHistoryRepository;
    private final ReadinessSnapshotRepository readinessSnapshotRepository;
    private final Clock clock;

    public AccountExportServiceImpl(UserRepository userRepository,
                                    GoalCatalog goalCatalog,
                                    UserSkillCatalog catalog,
                                    ProfileLinkRepository profileLinkRepository,
                                    ProfileProjectRepository profileProjectRepository,
                                    AnalysisHistoryRepository analysisHistoryRepository,
                                    ReadinessSnapshotRepository readinessSnapshotRepository,
                                    Clock clock) {
        this.userRepository = userRepository;
        this.goalCatalog = goalCatalog;
        this.catalog = catalog;
        this.profileLinkRepository = profileLinkRepository;
        this.profileProjectRepository = profileProjectRepository;
        this.analysisHistoryRepository = analysisHistoryRepository;
        this.readinessSnapshotRepository = readinessSnapshotRepository;
        this.clock = clock;
    }

    @Override
    public AccountExport export(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(AuthenticationRequiredException::new);
        List<CareerGoal> goals = goalCatalog.findAll(userId); // los base y los propios del usuario
        CareerGoal goal = user.getGoalId() == null ? null
                : goals.stream().filter(g -> g.getId().equals(user.getGoalId())).findFirst().orElse(null);
        List<ReadinessSnapshot> readiness = new ArrayList<>();
        for (CareerGoal g : goals) {
            readiness.addAll(readinessSnapshotRepository.findSince(userId, g.getId(), SINCE_ALWAYS));
        }
        readiness.sort(Comparator.comparing(ReadinessSnapshot::capturedOn).thenComparing(ReadinessSnapshot::goalId));
        return new AccountExport(
                user.getName(),
                user.getEmail(),
                user.getHeadline(),
                user.getCreatedAt(),
                goal,
                catalog.findAll(userId),
                profileLinkRepository.findByUserId(userId),
                profileProjectRepository.findByUserId(userId),
                analysisHistoryRepository.findLatest(userId, MAX_ANALYSES),
                readiness,
                goals,
                clock.instant());
    }
}
