package com.skillmap.api.domain.model;

import com.skillmap.api.domain.exception.InvalidSettingsException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiApiKeyTest {

    private static final String KEY = "test-key-NOT-REAL-0123456789abcdef";

    @Test
    void acceptsAndStripsPastedKey() {
        assertThat(GeminiApiKey.of("  " + KEY + "\n").value()).isEqualTo(KEY);
    }

    @Test
    void rejectsEmptyKey() {
        assertInvalid(null, "vacía");
        assertInvalid("   ", "vacía");
    }

    @Test
    void rejectsSpacesInside() {
        assertInvalid("test-key NOT-REAL-0123456789abcdef", "espacios");
        assertInvalid("test-key\tNOT-REAL-0123456789abcdef", "espacios");
    }

    @Test
    void rejectsUnreasonableLength() {
        assertInvalid("short-key", "entre 20 y 200");
        assertInvalid("k".repeat(GeminiApiKey.MAX_LENGTH + 1), "entre 20 y 200");
        assertThat(GeminiApiKey.of("k".repeat(GeminiApiKey.MIN_LENGTH)).value()).hasSize(20);
        assertThat(GeminiApiKey.of("k".repeat(GeminiApiKey.MAX_LENGTH)).value()).hasSize(200);
    }

    @Test
    void rejectsNonAsciiCharacters() {
        assertInvalid("test-key-NOT-REAL-0123456789abcdeñ", "ASCII");
    }

    @Test
    void toStringNeverShowsTheKey() {
        assertThat(GeminiApiKey.of(KEY).toString()).doesNotContain(KEY).doesNotContain("test-key");
    }

    private static void assertInvalid(String raw, String expectedMessagePart) {
        assertThatThrownBy(() -> GeminiApiKey.of(raw))
                .isInstanceOf(InvalidSettingsException.class)
                .hasMessageContaining(expectedMessagePart)
                .satisfies(e -> {
                    if (raw != null && !raw.isBlank()) {
                        assertThat(e.getMessage()).doesNotContain(raw.strip());
                    }
                });
    }
}
