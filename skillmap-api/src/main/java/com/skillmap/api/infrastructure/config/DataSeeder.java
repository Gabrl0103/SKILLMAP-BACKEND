package com.skillmap.api.infrastructure.config;

import com.skillmap.api.domain.factory.CareerGoalFactory;
import com.skillmap.api.domain.factory.SkillFactory;
import com.skillmap.api.domain.model.GoalSkill;
import com.skillmap.api.domain.repository.CareerGoalRepository;
import com.skillmap.api.domain.repository.SkillRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Carga datos de ejemplo al arrancar: habilidades y cinco objetivos laborales.
 * Solo actúa con la base vacía: la base persiste entre reinicios y no se debe pisar
 * la demanda calculada con ofertas reales. El progreso es de cada usuario (tabla user_skills):
 * aquí todas las habilidades nacen pendientes.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final SkillRepository skillRepository;
    private final CareerGoalRepository goalRepository;

    public DataSeeder(SkillRepository skillRepository, CareerGoalRepository goalRepository) {
        this.skillRepository = skillRepository;
        this.goalRepository = goalRepository;
    }

    @Override
    public void run(String... args) {
        boolean hasSkills = !skillRepository.findAll().isEmpty();
        boolean hasGoals = !goalRepository.findAll().isEmpty();
        if (hasSkills || hasGoals) {
            if (hasSkills != hasGoals) {
                log.warn("La base tiene {} pero no {}: no se siembra nada para no mezclar datos",
                        hasSkills ? "habilidades" : "objetivos", hasSkills ? "objetivos" : "habilidades");
            }
            return;
        }

        var react = seed("React", "Frontend", 95);
        var ts = seed("TypeScript", "Frontend", 88);
        var next = seed("Next.js", "Frontend", 71);
        var tailwind = seed("Tailwind CSS", "Frontend", 64);
        var testing = seed("Testing", "Calidad", 69);
        var java = seed("Java", "Backend", 82);
        var spring = seed("Spring Boot", "Backend", 74);
        var sql = seed("SQL", "Datos", 90);
        var docker = seed("Docker", "DevOps", 68);
        var python = seed("Python", "Datos", 92);
        var ml = seed("Machine Learning", "Datos", 78);
        var pandas = seed("Pandas", "Datos", 70);
        var stats = seed("Estadística", "Datos", 66);
        var aws = seed("AWS", "DevOps", 89);
        var kubernetes = seed("Kubernetes", "DevOps", 73);
        var terraform = seed("Terraform", "DevOps", 62);
        var cicd = seed("CI/CD", "DevOps", 76);
        var reactNative = seed("React Native", "Móvil", 80);
        var kotlin = seed("Kotlin", "Móvil", 67);
        var swift = seed("Swift", "Móvil", 63);

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

    /** Crea la habilidad y devuelve el id generado. */
    private Long seed(String name, String category, int demand) {
        return skillRepository.save(SkillFactory.createNew(name, category, demand)).getId();
    }
}
