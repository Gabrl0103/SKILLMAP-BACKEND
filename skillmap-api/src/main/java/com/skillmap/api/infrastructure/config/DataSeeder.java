package com.skillmap.api.infrastructure.config;

import com.skillmap.api.domain.factory.CareerGoalFactory;
import com.skillmap.api.domain.factory.SkillFactory;
import com.skillmap.api.domain.model.DemandSource;
import com.skillmap.api.domain.model.GoalSkill;
import com.skillmap.api.domain.model.SkillStatus;
import com.skillmap.api.domain.repository.CareerGoalRepository;
import com.skillmap.api.domain.repository.SkillRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/** Carga datos de ejemplo al arrancar: habilidades y cinco objetivos laborales. */
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
        var sql = seed("SQL", "Datos", 90, SkillStatus.MASTERED);
        var docker = seed("Docker", "DevOps", 68, SkillStatus.MASTERED);
        var python = seed("Python", "Datos", 92, SkillStatus.IN_PROGRESS);
        var ml = seed("Machine Learning", "Datos", 78, SkillStatus.PENDING);
        var pandas = seed("Pandas", "Datos", 70, SkillStatus.PENDING);
        var stats = seed("Estadística", "Datos", 66, SkillStatus.PENDING);
        var aws = seed("AWS", "DevOps", 89, SkillStatus.PENDING);
        var kubernetes = seed("Kubernetes", "DevOps", 73, SkillStatus.PENDING);
        var terraform = seed("Terraform", "DevOps", 62, SkillStatus.PENDING);
        var cicd = seed("CI/CD", "DevOps", 76, SkillStatus.IN_PROGRESS);
        var reactNative = seed("React Native", "Móvil", 80, SkillStatus.PENDING);
        var kotlin = seed("Kotlin", "Móvil", 67, SkillStatus.PENDING);
        var swift = seed("Swift", "Móvil", 63, SkillStatus.PENDING);

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

        goalRepository.save(CareerGoalFactory.createNew(
                "Data Scientist",
                "Análisis de datos y modelos predictivos con Python y Machine Learning.",
                List.of(new GoalSkill(python, 5), new GoalSkill(ml, 5), new GoalSkill(sql, 4),
                        new GoalSkill(pandas, 4), new GoalSkill(stats, 3))));

        goalRepository.save(CareerGoalFactory.createNew(
                "Cloud Architect",
                "Infraestructura escalable en la nube con AWS, contenedores e infraestructura como código.",
                List.of(new GoalSkill(aws, 5), new GoalSkill(kubernetes, 5), new GoalSkill(docker, 4),
                        new GoalSkill(terraform, 4), new GoalSkill(cicd, 3))));

        goalRepository.save(CareerGoalFactory.createNew(
                "Mobile Lead",
                "Apps nativas y multiplataforma, y liderazgo de equipos móviles.",
                List.of(new GoalSkill(reactNative, 5), new GoalSkill(ts, 4), new GoalSkill(kotlin, 3),
                        new GoalSkill(swift, 3), new GoalSkill(testing, 3))));
    }

    /** Crea la habilidad con su estado inicial y devuelve el id generado. */
    private Long seed(String name, String category, int demand, SkillStatus status) {
        var skill = SkillFactory.reconstruct(null, name, category, demand, status, DemandSource.SEEDED);
        return skillRepository.save(skill).getId();
    }
}
