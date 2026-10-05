package com.skillmap.api.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiApiKeyProviderTest {

    @TempDir
    Path dir;

    @Test
    void environmentVariableWinsOverConfigFile() throws IOException {
        var env = envWithConfigFile("gemini.api.key=from-file\n");
        env.setProperty("GEMINI_API_KEY", "  from-env  ");

        assertThat(new GeminiApiKeyProvider(env).getApiKey()).contains("from-env");
    }

    @Test
    void fallsBackToConfigFile() throws IOException {
        var env = envWithConfigFile("# ajustes\ngemini.api.key = from-file \n");

        assertThat(new GeminiApiKeyProvider(env).getApiKey()).contains("from-file");
    }

    @Test
    void ignoresByteOrderMarkFromNotepad() throws IOException {
        var env = envWithConfigFile("﻿gemini.api.key=from-file\n");

        assertThat(new GeminiApiKeyProvider(env).getApiKey()).contains("from-file");
    }

    @Test
    void blankEnvironmentVariableFallsBackToConfigFile() throws IOException {
        var env = envWithConfigFile("gemini.api.key=from-file\n");
        env.setProperty("GEMINI_API_KEY", "   ");

        assertThat(new GeminiApiKeyProvider(env).getApiKey()).contains("from-file");
    }

    @Test
    void readsFileChangesWithoutRestart() throws IOException {
        var env = envWithConfigFile("");
        var provider = new GeminiApiKeyProvider(env);
        assertThat(provider.getApiKey()).isEmpty();

        Files.writeString(dir.resolve("config.properties"), "gemini.api.key=added-later\n");

        assertThat(provider.getApiKey()).contains("added-later");
    }

    @Test
    void emptyWhenNoVariableAndNoFile() {
        var env = new MockEnvironment();
        env.setProperty("skillmap.config-file", dir.resolve("missing.properties").toString());

        assertThat(new GeminiApiKeyProvider(env).getApiKey()).isEmpty();
    }

    @Test
    void emptyWhenFileHasNoKey() throws IOException {
        var env = envWithConfigFile("otra.cosa=1\ngemini.api.key=\n");

        assertThat(new GeminiApiKeyProvider(env).getApiKey()).isEmpty();
    }

    private MockEnvironment envWithConfigFile(String content) throws IOException {
        Path file = dir.resolve("config.properties");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        var env = new MockEnvironment();
        env.setProperty("skillmap.config-file", file.toString());
        return env;
    }
}
