package com.skillmap.api.infrastructure.config;

import com.skillmap.api.domain.factory.CareerGoalFactory;
import com.skillmap.api.domain.factory.SkillFactory;
import com.skillmap.api.domain.model.GoalSkill;
import com.skillmap.api.domain.model.SkillStatus;
import com.skillmap.api.domain.repository.CareerGoalRepository;
import com.skillmap.api.domain.repository.SkillRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/** Carga datos de ejemplo al arrancar: habilidades y dos objetivos laborales. */
@Component
public class DataSeeder implements CommandLineRunner {

    private final SkillRepository skillRepository;
    private final CareerGoalRepository goalRepository;

    public DataSeeder(SkillRepository skillRepository, CareerGoalRepository goalRepository) {
        this.skillRepository = skillRepository;
        this.goalRepository = goalRepository;
    }

    @Override
    public void run(String... args) {
        if (!skillRepository.findAll().isEmpty()) {
            return;
        }

        var react = seed("React", "Frontend", 95, SkillStatus.MASTERED);
        var ts = seed("TypeScript", "Frontend", 88, SkillStatus.IN_PROGRESS);
        var next = seed("Next.js", "Frontend", 71, SkillStatus.PENDING);
        var tailwind = seed("Tailwind CSS", "Frontend", 64, SkillStatus.MASTERED);
        var testing = seed("Testing", "Calidad", 69, SkillStatus.PENDING);
        var java = seed("Java", "Backend", 82, SkillStatus.IN_PROGRESS);
        var spring = seed("Spring Boot", "Backend", 74, SkillStatus.IN_PROGRESS);
        var sql = seed("SQL", "Datos", 90, SkillStatus.PENDING);
        var docker = seed("Docker", "DevOps", 68, SkillStatus.PENDING);

        goalRepository.save(CareerGoalFactory.createNew(
                "Frontend Senior",
                "Interfaces web modernas, tipadas y bien probadas.",
                List.of(new GoalSkill(react, 5), new GoalSkill(ts, 5), new GoalSkill(next, 3),
                        new GoalSkill(tailwind, 2), new GoalSkill(testing, 3))));

        goalRepository.save(CareerGoalFactory.createNew(
                "Backend Java",
                "APIs robustas con Java y Spring Boot.",
                List.of(new GoalSkill(java, 5), new GoalSkill(spring, 5), new GoalSkill(sql, 4),
                        new GoalSkill(docker, 3), new GoalSkill(testing, 3))));
    }

    /** Crea la habilidad con su estado inicial y devuelve el id generado. */
    private Long seed(String name, String category, int demand, SkillStatus status) {
        var skill = SkillFactory.reconstruct(null, name, category, demand, status);
        return skillRepository.save(skill).getId();
    }
}
