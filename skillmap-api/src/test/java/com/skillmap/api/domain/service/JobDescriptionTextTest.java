package com.skillmap.api.domain.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JobDescriptionTextTest {

    @Test
    void sameOfferWithDifferentSpacingHasTheSameHash() {
        String hash = JobDescriptionText.hash("Buscamos React\ny Kafka");

        assertThat(hash).hasSize(64).matches("[0-9a-f]+");
        assertThat(JobDescriptionText.hash("  Buscamos   React y\r\n\tKafka ")).isEqualTo(hash);
        // Espacio de no separación (copiado de una web).
        assertThat(JobDescriptionText.hash("Buscamos React y Kafka")).isEqualTo(hash);
        // "é" como un carácter o como "e" + tilde combinada: el mismo texto.
        assertThat(JobDescriptionText.hash("Café")).isEqualTo(JobDescriptionText.hash("Café"));
    }

    @Test
    void differentTextOrCaseIsAnotherOffer() {
        assertThat(JobDescriptionText.hash("Buscamos Go")).isNotEqualTo(JobDescriptionText.hash("Buscamos go"));
        assertThat(JobDescriptionText.hash("Buscamos React")).isNotEqualTo(JobDescriptionText.hash("Buscamos Vue"));
    }

    @Test
    void titleIsTheFirstLineWithText() {
        assertThat(JobDescriptionText.title("\n\n   Desarrollador   Backend  \nSpring Boot")).isEqualTo("Desarrollador Backend");
    }

    @Test
    void longTitlesAreCutWithEllipsis() {
        String title = JobDescriptionText.title("Palabra ".repeat(30));

        assertThat(title.codePointCount(0, title.length())).isEqualTo(JobDescriptionText.TITLE_MAX_LENGTH);
        assertThat(title).endsWith("…").doesNotContain(" …");
    }

    @Test
    void emojisAreNotSplitWhenCutting() {
        String title = JobDescriptionText.title("🚀".repeat(100));

        assertThat(title).isEqualTo("🚀".repeat(JobDescriptionText.TITLE_MAX_LENGTH - 1) + "…");
    }
}
