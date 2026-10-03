package com.skillmap.api.domain.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class SkillMatcherTest {

    private static boolean matches(String skill, String text) {
        return SkillMatcher.forSkill(skill).matches(text);
    }

    @Test
    void javaDoesNotMatchJavaScript() {
        assertThat(matches("Java", "Senior JavaScript developer")).isFalse();
        assertThat(matches("Java", "Experience with Java, Spring and SQL")).isTrue();
        assertThat(matches("Java", "We use java.")).isTrue();
    }

    @Test
    void javaScriptDoesNotNeedJava() {
        assertThat(matches("JavaScript", "Java backend developer")).isFalse();
        assertThat(matches("JavaScript", "javascript and HTML")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Statistics with R and Python",
            "Python/R",
            "R, Python or Julia",
            "Knowledge of R."
    })
    void singleLetterRMatchesAsAWord(String text) {
        assertThat(matches("R", text)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Our R&D team",
            "SAP R/3 experience",
            "React developer",
            "remote friendly, r is not a skill here",
            "Rust and Ruby"
    })
    void singleLetterRDoesNotMatchElsewhere(String text) {
        assertThat(matches("R", text)).isFalse();
    }

    @Test
    void namesWithSymbolsMatchExactly() {
        assertThat(matches("Next.js", "Built with Next.js and React")).isTrue();
        assertThat(matches("Next.js", "Your next step in your career")).isFalse();
        assertThat(matches("SQL", "NoSQL databases like MongoDB")).isFalse();
        assertThat(matches("SQL", "Strong SQL skills")).isTrue();
    }

    @Test
    void multiWordNamesAcceptHyphens() {
        assertThat(matches("Machine Learning", "machine-learning pipelines")).isTrue();
        assertThat(matches("Spring Boot", "spring boot microservices")).isTrue();
        assertThat(matches("React Native", "React-Native apps")).isTrue();
    }

    @Test
    void ciCdAliases() {
        assertThat(matches("CI/CD", "Own our CI/CD pipelines")).isTrue();
        assertThat(matches("CI/CD", "Experience with CI CD tooling")).isTrue();
        assertThat(matches("CI/CD", "ci-cd with GitHub Actions")).isTrue();
        assertThat(matches("CI/CD", "Continuous integration and delivery")).isTrue();
        assertThat(matches("CI/CD", "We are a CI company")).isFalse();
    }

    @Test
    void spanishCatalogNamesMatchEnglishPostings() {
        assertThat(matches("Estadística", "Solid background in statistics")).isTrue();
        assertThat(matches("Estadística", "Statistical modelling")).isTrue();
        assertThat(matches("Estadística", "Conocimientos de estadística")).isTrue();
        assertThat(matches("Estadística", "Data engineer, Spark and Airflow")).isFalse();
    }

    // ---------- Nombres que son palabras comunes del inglés ----------

    @ParameterizedTest
    @ValueSource(strings = {
            "Frontend with React and Redux",
            "Experience in React.js",
            "react.js or vue",
            "ReactJS, TypeScript",
            "React-Native apps"
    })
    void reactCountsAsTheProduct(String text) {
        assertThat(matches("React", text)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "You react quickly to incidents",
            "able to react to customer feedback",
            "REACT FAST TO CHANGES"
    })
    void reactDoesNotCountAsTheVerb(String text) {
        assertThat(matches("React", text)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Native iOS apps in Swift and SwiftUI",
            "Swift developer for our Apple platforms",
            "Swift, Kotlin",
            "Mobile engineer (Swift)",
            "Build with Swift in Xcode"
    })
    void swiftCountsWithAppleOrMobileContext(String text) {
        assertThat(matches("Swift", text)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Swift response times for our customers",
            "We value swift delivery on iOS and Android",
            "Our payments run on SWIFT and SEPA",
            "Kotlin backend services"
    })
    void swiftDoesNotCountWithoutContextOrInLowercase(String text) {
        assertThat(matches("Swift", text)).isFalse();
    }

    @Test
    void swiftContextMustBeNearby() {
        String farAway = "Swift onboarding for every hire. " + "x".repeat(SkillMatcher.CONTEXT_WINDOW) + " iOS";
        assertThat(matches("Swift", farAway)).isFalse();
        // Una mención sin contexto no impide que cuente otra con contexto.
        assertThat(matches("Swift", farAway + " app written in Swift")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Strong unit testing habits",
            "Test automation with Selenium",
            "QA Automation engineer",
            "Tests with Jest and React Testing Library",
            "Cypress or Playwright",
            "end-to-end tests",
            "End to end testing"
    })
    void testingCountsOnlyForConcretePractices(String text) {
        assertThat(matches("Testing", text)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "We are testing new ideas",
            "Take end-to-end ownership of features",
            "Own a project end to end",
            "Testing",
            "QA engineer, manual only",
            "a jest of a job, cypress trees"
    })
    void testingDoesNotCountAsTheBareWord(String text) {
        assertThat(matches("Testing", text)).isFalse();
    }

    @Test
    void abbreviationAliases() {
        assertThat(matches("Kubernetes", "Deploying to K8s clusters")).isTrue();
        assertThat(matches("AWS", "Amazon Web Services (Lambda, S3)")).isTrue();
        assertThat(matches("SQL", "PostgreSQL or MySQL")).isTrue();
        assertThat(matches("Tailwind CSS", "Styled with Tailwind")).isTrue();
    }

    @Test
    void twoLetterAliasesAreCaseSensitive() {
        assertThat(matches("Machine Learning", "ML/AI engineer")).isTrue();
        assertThat(matches("Machine Learning", "Dosis de 5 ml")).isFalse();
        assertThat(matches("Machine Learning", "HTML and CSS")).isFalse();
    }

    @Test
    void nullOrEmptyTextNeverMatches() {
        assertThat(matches("Java", null)).isFalse();
        assertThat(matches("Java", "")).isFalse();
    }
}
