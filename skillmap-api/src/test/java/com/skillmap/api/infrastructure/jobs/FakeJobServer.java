package com.skillmap.api.infrastructure.jobs;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/** Servidor HTTP local que imita a una bolsa de empleo para probar los adaptadores. */
final class FakeJobServer implements AutoCloseable {

    record Response(int status, String contentType, String body, long delayMillis) {
        static Response json(String body) {
            return new Response(200, "application/json", body, 0);
        }

        static Response status(int status) {
            return new Response(status, "application/json", "{\"error\":\"x\"}", 0);
        }
    }

    private final HttpServer server;
    final List<URI> requests = new CopyOnWriteArrayList<>();

    FakeJobServer(Function<URI, Response> handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.add(exchange.getRequestURI());
            Response response = handler.apply(exchange.getRequestURI());
            if (response.delayMillis() > 0) {
                try {
                    Thread.sleep(response.delayMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", response.contentType());
            exchange.sendResponseHeaders(response.status(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/api";
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
