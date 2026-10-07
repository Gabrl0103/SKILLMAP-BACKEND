package com.skillmap.api.infrastructure.config;

import com.skillmap.api.domain.exception.SettingsStorageException;
import com.skillmap.api.domain.model.GeminiApiKey;
import com.skillmap.api.domain.model.GeminiKeySource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Lectura con precedencia y escritura de gemini.api.key en config.properties (carpeta temporal). */
@ExtendWith(OutputCaptureExtension.class)
class PropertiesFileGeminiKeyStoreTest {

    /** Sin el formato AIza...: así ningún filtro de logs la tapa y se ve si de verdad no se registra. */
    private static final String KEY = "test-key-NOT-REAL-0123456789abcdef";

    @TempDir
    Path dir;

    private final MockEnvironment env = new MockEnvironment();

    // ---------- Origen de la key ----------

    @Test
    void sourceIsNoneWithoutVariableNorFile() {
        assertThat(store().activeSource()).isEqualTo(GeminiKeySource.NONE);
        assertThat(store().findKey()).isEmpty();
    }

    @Test
    void sourceIsFileWhenOnlyTheFileHasIt() throws IOException {
        writeConfig("gemini.api.key=" + KEY + "\n");

        assertThat(store().activeSource()).isEqualTo(GeminiKeySource.FILE);
        assertThat(store().findKey()).contains(KEY);
    }

    @Test
    void environmentVariableHasPrecedenceOverFile() throws IOException {
        writeConfig("gemini.api.key=" + KEY + "\n");
        env.setProperty("GEMINI_API_KEY", "env-key-0123456789abcdef");

        assertThat(store().activeSource()).isEqualTo(GeminiKeySource.ENV);
        assertThat(store().findKey()).contains("env-key-0123456789abcdef");
    }

    // ---------- Guardar ----------

    @Test
    void createsDirectoryAndFileWhenMissing() throws IOException {
        Path nested = dir.resolve("SkillMap");
        var store = new PropertiesFileGeminiKeyStore(env, nested);

        store.save(GeminiApiKey.of(KEY));

        assertThat(loadProperties(nested.resolve("config.properties")).getProperty("gemini.api.key")).isEqualTo(KEY);
        assertThat(store.activeSource()).isEqualTo(GeminiKeySource.FILE);
    }

    @Test
    void preservesOtherPropertiesCommentsAndOrder() throws IOException {
        writeConfig("# Mis ajustes\nantes=1\ngemini.api.key=old-key\n! otro comentario\ndespues = dos\n");

        store().save(GeminiApiKey.of(KEY));

        assertThat(readConfig()).isEqualTo(
                "# Mis ajustes\nantes=1\ngemini.api.key=" + KEY + "\n! otro comentario\ndespues = dos\n");
    }

    @Test
    void appendsKeyWhenFileDoesNotHaveIt() throws IOException {
        writeConfig("otra.cosa=1");

        store().save(GeminiApiKey.of(KEY));

        // Sin saltos de línea de los que copiar el estilo: el del sistema.
        String nl = System.lineSeparator();
        assertThat(readConfig()).isEqualTo("otra.cosa=1" + nl + "gemini.api.key=" + KEY + nl);
    }

    @Test
    void keepsWindowsLineEndingsAndByteOrderMark() throws IOException {
        Files.write(configFile(), ("﻿a=1\r\ngemini.api.key=old\r\nb=2\r\n").getBytes(StandardCharsets.UTF_8));

        store().save(GeminiApiKey.of(KEY));

        assertThat(Files.readString(configFile(), StandardCharsets.UTF_8))
                .isEqualTo("﻿a=1\r\ngemini.api.key=" + KEY + "\r\nb=2\r\n");
        assertThat(store().findKey()).contains(KEY);
    }

    @Test
    void keepsBytesThatAreNotUtf8() throws IOException {
        byte[] latin1 = "nombre=María\n".getBytes(StandardCharsets.ISO_8859_1);
        Files.write(configFile(), latin1);

        store().save(GeminiApiKey.of(KEY));

        byte[] expected = ("nombre=María\ngemini.api.key=" + KEY + "\n").getBytes(StandardCharsets.ISO_8859_1);
        assertThat(Files.readAllBytes(configFile())).isEqualTo(expected);
    }

    @Test
    void replacesDuplicatesAndContinuationLines() throws IOException {
        writeConfig("gemini.api.key = old\\\n    continued\na=1\ngemini.api.key:other\n# gemini.api.key=comment\n");

        store().save(GeminiApiKey.of(KEY));

        assertThat(readConfig()).isEqualTo("gemini.api.key=" + KEY + "\na=1\n# gemini.api.key=comment\n");
        assertThat(loadProperties(configFile()).getProperty("a")).isEqualTo("1");
    }

    @Test
    void continuationOfAnotherPropertyIsNotMistakenForTheKey() throws IOException {
        writeConfig("lista=uno,\\\ngemini.api.key=no-es-la-key\n");

        store().save(GeminiApiKey.of(KEY));

        Properties saved = loadProperties(configFile());
        assertThat(saved.getProperty("lista")).isEqualTo("uno,gemini.api.key=no-es-la-key");
        assertThat(saved.getProperty("gemini.api.key")).isEqualTo(KEY);
    }

    @Test
    void escapesBackslashes() throws IOException {
        store().save(GeminiApiKey.of("key\\with\\backslashes-0123456789"));

        assertThat(store().findKey()).contains("key\\with\\backslashes-0123456789");
    }

    @Test
    void savesEvenWhenEnvironmentVariableWins() throws IOException {
        env.setProperty("GEMINI_API_KEY", "env-key-0123456789abcdef");

        store().save(GeminiApiKey.of(KEY));

        assertThat(loadProperties(configFile()).getProperty("gemini.api.key")).isEqualTo(KEY);
        assertThat(store().activeSource()).isEqualTo(GeminiKeySource.ENV);
    }

    @Test
    void failureDoesNotExposeTheKey(CapturedOutput output) throws IOException {
        // Una carpeta donde debería estar el archivo: no se puede escribir.
        Files.createDirectories(configFile().resolve("ocupado"));

        assertThatThrownBy(() -> store().save(GeminiApiKey.of(KEY)))
                .isInstanceOf(SettingsStorageException.class)
                .hasNoCause()
                .message().doesNotContain(KEY);
        assertThat(output.getAll()).doesNotContain(KEY);
    }

    @Test
    void savingNeverLogsTheKey(CapturedOutput output) throws IOException {
        writeConfig("a=1\n");

        store().save(GeminiApiKey.of(KEY));
        store().findKey();
        store().activeSource();

        assertThat(output.getAll()).doesNotContain(KEY);
    }

    private PropertiesFileGeminiKeyStore store() {
        return new PropertiesFileGeminiKeyStore(env, dir);
    }

    private Path configFile() {
        return dir.resolve("config.properties");
    }

    private void writeConfig(String content) throws IOException {
        Files.writeString(configFile(), content, StandardCharsets.UTF_8);
    }

    private String readConfig() throws IOException {
        return Files.readString(configFile(), StandardCharsets.UTF_8);
    }

    private static Properties loadProperties(Path file) throws IOException {
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(file, StandardCharsets.ISO_8859_1)) {
            properties.load(reader);
        }
        return properties;
    }
}
