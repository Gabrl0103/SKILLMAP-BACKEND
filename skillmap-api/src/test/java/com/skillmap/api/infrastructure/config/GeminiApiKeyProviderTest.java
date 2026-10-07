package com.skillmap.api.infrastructure.config;

import com.skillmap.api.domain.model.GeminiApiKey;
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

        assertThat(provider(env).getApiKey()).contains("from-env");
    }

    @Test
    void fallsBackToConfigFile() throws IOException {
        var env = envWithConfigFile("# ajustes\ngemini.api.key = from-file \n");

        assertThat(provider(env).getApiKey()).contains("from-file");
    }

    @Test
    void ignoresByteOrderMarkFromNotepad() throws IOException {
        var env = envWithConfigFile("﻿gemini.api.key=from-file\n");

        assertThat(provider(env).getApiKey()).contains("from-file");
    }

    @Test
    void blankEnvironmentVariableFallsBackToConfigFile() throws IOException {
        var env = envWithConfigFile("gemini.api.key=from-file\n");
        env.setProperty("GEMINI_API_KEY", "   ");

        assertThat(provider(env).getApiKey()).contains("from-file");
    }

    @Test
    void readsFileChangesWithoutRestart() throws IOException {
        var provider = provider(envWithConfigFile(""));
        assertThat(provider.getApiKey()).isEmpty();

        Files.writeString(dir.resolve("config.properties"), "gemini.api.key=added-later\n");

        assertThat(provider.getApiKey()).contains("added-later");
    }

    @Test
    void readsKeySavedFromSettingsWithoutRestart() throws IOException {
        var env = envWithConfigFile("gemini.api.key=old-key-0123456789abcdef\n");
        var store = new PropertiesFileGeminiKeyStore(env, dir);
        var provider = new GeminiApiKeyProvider(store);
        assertThat(provider.getApiKey()).contains("old-key-0123456789abcdef");

        store.save(GeminiApiKey.of("new-key-0123456789abcdef"));

        assertThat(provider.getApiKey()).contains("new-key-0123456789abcdef");
    }

    @Test
    void emptyWhenNoVariableAndNoFile() {
        assertThat(provider(new MockEnvironment()).getApiKey()).isEmpty();
    }

    @Test
    void emptyWhenFileHasNoKey() throws IOException {
        var env = envWithConfigFile("otra.cosa=1\ngemini.api.key=\n");

        assertThat(provider(env).getApiKey()).isEmpty();
    }

    @Test
    void configFileIsInsideConfigDir() {
        assertThat(provider(new MockEnvironment()).getConfigFile()).contains(dir.resolve("config.properties"));
    }

    private GeminiApiKeyProvider provider(MockEnvironment env) {
        return new GeminiApiKeyProvider(new PropertiesFileGeminiKeyStore(env, dir));
    }

    private MockEnvironment envWithConfigFile(String content) throws IOException {
        Files.writeString(dir.resolve("config.properties"), content, StandardCharsets.UTF_8);
        return new MockEnvironment();
    }
}
