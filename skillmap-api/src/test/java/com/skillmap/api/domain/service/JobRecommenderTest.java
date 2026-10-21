package com.skillmap.api.domain.service;

import com.skillmap.api.domain.model.DemandSource;
import com.skillmap.api.domain.model.JobPosting;
import com.skillmap.api.domain.model.JobRecommendation;
import com.skillmap.api.domain.model.JobRecommendation.MissingSkill;
import com.skillmap.api.domain.model.Skill;
import com.skillmap.api.domain.model.SkillStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JobRecommenderTest {

    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");
    private static final long JAVA = 1, SPRING = 2, SQL = 3, REACT = 4, PYTHON = 5;
    private static final Set<Long> GOAL = Set.of(JAVA, SPRING, SQL);

    @Test
    void ordersByMatchThenGoalSkillsThenNewest() {
        JobPosting a = posting(1, 5);  // Java, Spring, SQL
        JobPosting b = posting(2, 1);  // Java, React
        JobPosting c = posting(3, 2);  // Spring, React
        JobPosting d = posting(4, 3);  // Spring, SQL
        Map<JobPosting, Set<Long>> detected = Map.of(
                a, Set.of(JAVA, SPRING, SQL), b, Set.of(JAVA, REACT), c, Set.of(SPRING, REACT), d, Set.of(SPRING, SQL));
        List<Skill> catalog = catalog(Map.of(JAVA, SkillStatus.MASTERED));

        List<JobRecommendation> result = JobRecommender.recommend(detected.keySet(), detected::get, catalog, GOAL, 10);

        // b: 1/2 = 50; a: 1/3 = 33; d y c: 0, d con 2 del objetivo y c con 1.
        assertThat(result).extracting(r -> r.posting().id()).containsExactly(2L, 1L, 4L, 3L);
        assertThat(result).extracting(JobRecommendation::matchPercentage).containsExactly(50, 33, 0, 0);
    }

    @Test
    void sameMatchAndGoalSkillsGoesNewestFirst() {
        JobPosting older = posting(1, 9);
        JobPosting newer = posting(2, 1);
        Map<JobPosting, Set<Long>> detected = Map.of(older, Set.of(JAVA, REACT), newer, Set.of(SQL, REACT));

        List<JobRecommendation> result = JobRecommender.recommend(detected.keySet(), detected::get, catalog(Map.of()), GOAL, 10);

        assertThat(result).extracting(r -> r.posting().id()).containsExactly(2L, 1L);
    }

    @Test
    void inProgressCountsHalfAndMissingListsInProgressFirst() {
        JobPosting a = posting(1, 1);
        Map<JobPosting, Set<Long>> detected = Map.of(a, Set.of(JAVA, SPRING, SQL, REACT));
        List<Skill> catalog = catalog(Map.of(JAVA, SkillStatus.MASTERED, SQL, SkillStatus.IN_PROGRESS));

        JobRecommendation r = JobRecommender.recommend(detected.keySet(), detected::get, catalog, GOAL, 10).get(0);

        // (1 + 0,5) / 4 = 37,5 → 38
        assertThat(r.matchPercentage()).isEqualTo(38);
        assertThat(r.goalSkillCount()).isEqualTo(3);
        assertThat(r.masteredSkills()).containsExactly("Java");
        assertThat(r.missingSkills()).containsExactly(
                new MissingSkill("SQL", SkillStatus.IN_PROGRESS),
                new MissingSkill("Spring Boot", SkillStatus.PENDING),
                new MissingSkill("React", SkillStatus.PENDING));
    }

    @Test
    void skipsPostingsWithoutGoalSkillsOrWithFewerThanTwoSkills() {
        JobPosting noGoal = posting(1, 1);
        JobPosting single = posting(2, 1);
        JobPosting none = posting(3, 1);
        JobPosting relevant = posting(4, 1);
        Map<JobPosting, Set<Long>> detected = Map.of(
                noGoal, Set.of(REACT, PYTHON), single, Set.of(JAVA), none, Set.of(), relevant, Set.of(JAVA, PYTHON));

        List<JobRecommendation> result = JobRecommender.recommend(detected.keySet(), detected::get,
                catalog(Map.of(JAVA, SkillStatus.MASTERED)), GOAL, 10);

        assertThat(result).extracting(r -> r.posting().id()).containsExactly(4L);
    }

    @Test
    void respectsTheLimit() {
        List<JobPosting> postings = List.of(posting(1, 1), posting(2, 2), posting(3, 3));

        List<JobRecommendation> result = JobRecommender.recommend(postings, p -> Set.of(JAVA, SQL), catalog(Map.of()), GOAL, 2);

        assertThat(result).extracting(r -> r.posting().id()).containsExactly(1L, 2L);
    }

    private static JobPosting posting(long id, int daysAgo) {
        return new JobPosting(id, "Arbeitnow", "ext-" + id, "Oferta " + id, "ACME", "Remote", true, "", "https://example.com/" + id,
                "texto", NOW.minus(Duration.ofDays(daysAgo)), NOW);
    }

    /** Catálogo en orden de id; las habilidades sin estado indicado están pendientes. */
    private static List<Skill> catalog(Map<Long, SkillStatus> statuses) {
        return List.of(skill(JAVA, "Java", statuses), skill(SPRING, "Spring Boot", statuses), skill(SQL, "SQL", statuses),
                skill(REACT, "React", statuses), skill(PYTHON, "Python", statuses));
    }

    private static Skill skill(long id, String name, Map<Long, SkillStatus> statuses) {
        return new Skill(id, name, "Cat", 50, statuses.getOrDefault(id, SkillStatus.PENDING), DemandSource.SEEDED);
    }
}
