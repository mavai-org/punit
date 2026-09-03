package org.mavai.punit.lm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A local endpoint standing in for a vendor: replays a canned response
 * and records every request (body and headers) for wire-shape
 * assertions — punit-lm's adapters are exercised end to end without
 * the network.
 */
final class StubLlm implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Long enough that a deadline measured in tens of ms strikes first. */
    private static final long STALL_MILLIS = 30_000;

    private final HttpServer server;
    private final List<JsonNode> requestBodies = new ArrayList<>();
    private final List<Map<String, List<String>>> requestHeaders = new ArrayList<>();
    private volatile int status = 200;
    private volatile String response = "{}";
    private volatile boolean stall;
    private final CountDownLatch closed = new CountDownLatch(1);

    private StubLlm(HttpServer server) {
        this.server = server;
    }

    static StubLlm start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            StubLlm stub = new StubLlm(server);
            server.createContext("/", exchange -> {
                byte[] request = exchange.getRequestBody().readAllBytes();
                synchronized (stub) {
                    stub.requestBodies.add(MAPPER.readTree(request));
                    stub.requestHeaders.add(exchange.getRequestHeaders());
                }
                if (stub.stall) {
                    // Accepted, and then silent: the shape of the field
                    // failure — a connected socket with nothing coming
                    // back, which a connect-only deadline never catches.
                    // The latch is held until close(), so the silence
                    // outlasts any deadline under test without the
                    // handler thread outliving the test that started it.
                    try {
                        stub.closed.await(STALL_MILLIS, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                    exchange.close();
                    return;
                }
                byte[] body = stub.response.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(stub.status, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            server.start();
            return stub;
        } catch (IOException error) {
            throw new IllegalStateException("cannot start the stub endpoint", error);
        }
    }

    /** The stub's URL, for {@code mavai.llm.endpoint}. */
    String endpoint() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/";
    }

    /** Accept the request, then never answer. */
    StubLlm stalling() {
        this.stall = true;
        return this;
    }

    StubLlm respond(int status, String body) {
        this.status = status;
        this.response = body;
        return this;
    }

    /** A canned OpenAI-compatible completion carrying the given text. */
    StubLlm completeWith(String text) {
        return respond(200, "{\"choices\":[{\"message\":{\"content\":"
                + quote(text) + "}}]}");
    }

    /** A canned Anthropic messages response carrying the given blocks. */
    StubLlm anthropicWith(String blocksJson) {
        return respond(200, "{\"content\":[" + blocksJson + "]}");
    }

    /** A canned Ollama chat response carrying the given text. */
    StubLlm ollamaWith(String text) {
        return respond(200, "{\"message\":{\"content\":" + quote(text) + "}}");
    }

    synchronized JsonNode lastRequest() {
        return requestBodies.get(requestBodies.size() - 1);
    }

    synchronized Map<String, List<String>> lastHeaders() {
        return requestHeaders.get(requestHeaders.size() - 1);
    }

    synchronized int requestCount() {
        return requestBodies.size();
    }

    private static String quote(String text) {
        try {
            return MAPPER.writeValueAsString(text);
        } catch (IOException error) {
            throw new IllegalStateException(error);
        }
    }

    @Override
    public void close() {
        closed.countDown();
        server.stop(0);
    }
}
