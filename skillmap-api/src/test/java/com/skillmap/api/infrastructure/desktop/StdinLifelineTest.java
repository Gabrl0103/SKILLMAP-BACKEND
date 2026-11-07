package com.skillmap.api.infrastructure.desktop;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class StdinLifelineTest {

    @Test
    void exitsWhenStdinCloses() {
        AtomicInteger exits = new AtomicInteger();
        var input = new ByteArrayInputStream("cualquier cosa\n".getBytes(StandardCharsets.UTF_8));

        new StdinLifeline(input, exits::incrementAndGet).watch();

        assertThat(exits).hasValue(1);
    }

    @Test
    void exitsWhenStdinFails() {
        AtomicInteger exits = new AtomicInteger();
        InputStream broken = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("tubería rota");
            }
        };

        new StdinLifeline(broken, exits::incrementAndGet).watch();

        assertThat(exits).hasValue(1);
    }
}
