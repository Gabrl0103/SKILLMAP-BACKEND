package com.skillmap.api.application.service.impl;

import com.skillmap.api.application.service.CareerGoalService;
import com.skillmap.api.application.service.ReadinessHistoryService;
import com.skillmap.api.domain.exception.InvalidHistoryRangeException;
import com.skillmap.api.domain.model.CareerGoal;
import com.skillmap.api.domain.model.ReadinessSnapshot;
import com.skillmap.api.domain.repository.ReadinessSnapshotRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * El % sale de CareerGoalService.getReadiness (CareerGoal.calculateReadiness): no hay otra fórmula.
 * El día se cuenta en la zona horaria del equipo, que en la app de escritorio es la del usuario.
 */
@Service
public class ReadinessHistoryServiceImpl implements ReadinessHistoryService {

    private final CareerGoalService goalService;
    private final ReadinessSnapshotRepository snapshotRepository;
    private final Clock clock;

    public ReadinessHistoryServiceImpl(CareerGoalService goalService, ReadinessSnapshotRepository snapshotRepository,
                                       Clock clock) {
        this.goalService = goalService;
        this.snapshotRepository = snapshotRepository;
        this.clock = clock;
    }

    @Override
    public void recordSkillChange(Long userId, Long skillId) {
        for (CareerGoal goal : goalService.getAllGoals()) {
            if (goal.getSkills().stream().anyMatch(gs -> gs.skillId().equals(skillId))) {
                recordGoal(userId, goal.getId());
            }
        }
    }

    @Override
    public void recordGoal(Long userId, Long goalId) {
        record(userId, goalId, goalService.getReadiness(userId, goalId).readinessPercentage());
    }

    @Override
    public List<ReadinessSnapshot> getHistory(Long userId, Long goalId, int days) {
        if (days < 1 || days > MAX_DAYS) {
            throw new InvalidHistoryRangeException(MAX_DAYS);
        }
        goalService.getGoalById(goalId); // 404 si no existe
        if (snapshotRepository.findLatest(userId, goalId).isEmpty()) {
            recordGoal(userId, goalId);
        }
        return snapshotRepository.findSince(userId, goalId, today().minusDays(days - 1L));
    }

    /**
     * Igual al último registro: nada. Si el último es de hoy se actualiza; si no, se crea el de hoy.
     * synchronized: dos cambios simultáneos del mismo día no intentan crear dos filas.
     */
    private synchronized void record(Long userId, Long goalId, int percentage) {
        Optional<ReadinessSnapshot> latest = snapshotRepository.findLatest(userId, goalId);
        if (latest.isPresent() && latest.get().percentage() == percentage) {
            return;
        }
        LocalDate today = today();
        Long id = latest.filter(s -> s.capturedOn().equals(today)).map(ReadinessSnapshot::id).orElse(null);
        snapshotRepository.save(new ReadinessSnapshot(id, userId, goalId, percentage, today, now()));
    }

    private Instant now() {
        return clock.instant();
    }

    private LocalDate today() {
        return LocalDate.ofInstant(now(), ZoneId.systemDefault());
    }
}
