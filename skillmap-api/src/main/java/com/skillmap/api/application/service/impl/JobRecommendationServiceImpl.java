package com.skillmap.api.application.service.impl;

import com.skillmap.api.application.service.JobRecommendationService;
import com.skillmap.api.domain.exception.CareerGoalNotFoundException;
import com.skillmap.api.domain.exception.InvalidRecommendationLimitException;
import com.skillmap.api.domain.model.CareerGoal;
import com.skillmap.api.domain.model.GoalSkill;
import com.skillmap.api.domain.model.JobPosting;
import com.skillmap.api.domain.model.JobRecommendation;
import com.skillmap.api.domain.model.Skill;
import com.skillmap.api.domain.model.User;
import com.skillmap.api.domain.repository.CareerGoalRepository;
import com.skillmap.api.domain.repository.JobPostingRepository;
import com.skillmap.api.domain.repository.UserRepository;
import com.skillmap.api.domain.service.JobRecommender;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Sin IA ni llamadas externas: las habilidades de cada oferta salen de SkillMatcher (JobSkillDetector). */
@Service
public class JobRecommendationServiceImpl implements JobRecommendationService {

    private final UserRepository userRepository;
    private final CareerGoalRepository goalRepository;
    private final JobPostingRepository postingRepository;
    private final UserSkillCatalog userSkills;
    private final JobSkillDetector detector;

    public JobRecommendationServiceImpl(UserRepository userRepository, CareerGoalRepository goalRepository,
                                        JobPostingRepository postingRepository, UserSkillCatalog userSkills,
                                        JobSkillDetector detector) {
        this.userRepository = userRepository;
        this.goalRepository = goalRepository;
        this.postingRepository = postingRepository;
        this.userSkills = userSkills;
        this.detector = detector;
    }

    @Override
    public List<JobRecommendation> recommend(Long userId, Long goalId, int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new InvalidRecommendationLimitException(MAX_LIMIT);
        }
        Optional<CareerGoal> goal = goalId == null
                ? goalOf(userId)
                : Optional.of(goalRepository.findById(goalId).orElseThrow(() -> new CareerGoalNotFoundException(goalId)));
        if (goal.isEmpty()) {
            return List.of();
        }
        Set<Long> goalSkillIds = goal.get().getSkills().stream().map(GoalSkill::skillId).collect(Collectors.toSet());
        List<Skill> catalog = userSkills.findAll(userId);
        List<JobPosting> postings = postingRepository.findAll();
        Map<Long, Set<Long>> detected = detector.detect(postings, catalog);
        return JobRecommender.recommend(postings, p -> detected.getOrDefault(p.id(), Set.of()),
                catalog, goalSkillIds, limit);
    }

    /** El objetivo guardado del usuario; si no tiene (o ya no existe), el primero. */
    private Optional<CareerGoal> goalOf(Long userId) {
        Optional<CareerGoal> saved = userRepository.findById(userId)
                .map(User::getGoalId)
                .flatMap(goalRepository::findById);
        return saved.isPresent() ? saved : goalRepository.findAll().stream().findFirst();
    }
}
