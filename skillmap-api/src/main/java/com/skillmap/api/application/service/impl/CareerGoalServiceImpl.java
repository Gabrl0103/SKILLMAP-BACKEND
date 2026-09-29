package com.skillmap.api.application.service.impl;

import com.skillmap.api.application.service.CareerGoalService;
import com.skillmap.api.domain.exception.CareerGoalNotFoundException;
import com.skillmap.api.domain.model.CareerGoal;
import com.skillmap.api.domain.model.GoalReadiness;
import com.skillmap.api.domain.model.GoalSkill;
import com.skillmap.api.domain.model.Skill;
import com.skillmap.api.domain.model.SkillStatus;
import com.skillmap.api.domain.repository.CareerGoalRepository;
import com.skillmap.api.domain.repository.SkillRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CareerGoalServiceImpl implements CareerGoalService {

    private final CareerGoalRepository goalRepository;
    private final SkillRepository skillRepository;

    public CareerGoalServiceImpl(CareerGoalRepository goalRepository, SkillRepository skillRepository) {
        this.goalRepository = goalRepository;
        this.skillRepository = skillRepository;
    }

    @Override
    public List<CareerGoal> getAllGoals() {
        return goalRepository.findAll();
    }

    @Override
    public CareerGoal getGoalById(Long id) {
        return goalRepository.findById(id).orElseThrow(() -> new CareerGoalNotFoundException(id));
    }

    @Override
    public GoalReadiness getReadiness(Long goalId) {
        CareerGoal goal = getGoalById(goalId);
        Set<Long> goalSkillIds = goal.getSkills().stream().map(GoalSkill::skillId).collect(Collectors.toSet());

        // Solo las habilidades que pertenecen a este objetivo.
        Map<Long, Skill> goalSkills = skillRepository.findAll().stream()
                .filter(s -> goalSkillIds.contains(s.getId()))
                .collect(Collectors.toMap(Skill::getId, Function.identity()));

        List<Skill> gaps = goalSkills.values().stream()
                .filter(s -> s.getStatus() != SkillStatus.MASTERED)
                .sorted(Comparator.comparingInt(Skill::getDemandPercentage).reversed())
                .toList();

        int total = goal.getSkills().size();
        int mastered = (int) goalSkills.values().stream().filter(s -> s.getStatus() == SkillStatus.MASTERED).count();
        int readiness = goal.calculateReadiness(goalSkills.values());

        return new GoalReadiness(goal, readiness, mastered, total, gaps);
    }
}
