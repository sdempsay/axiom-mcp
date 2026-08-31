package org.dempsay.axiom.mcp;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.dempsay.axiom.mcp.ingest.CatalogFetcher;
import org.dempsay.axiom.mcp.ingest.CatalogIngest;
import org.dempsay.axiom.mcp.ingest.Gav;
import org.dempsay.axiom.mcp.ingest.IndexStore;
import org.dempsay.utils.exceptional.api.ExceptionalResponse;
import org.dempsay.utils.exceptional.api.ExceptionalSupplier;

/**
 * Local HTTP API for the live catalog process.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
@SuppressWarnings("checkstyle:IllegalImport")
public final class HttpApi {

    private static final ObjectMapper JSON = new ObjectMapper();

    private HttpApi() { }

    /**
     * Starts HTTP on {@code bind}. Port 0 is allowed for tests.
     *
     * @param bind listen address
     * @param store open index store
     * @param fetcher Maven Resolver fetch used by {@code POST /catalogs}
     * @return running server
     */
    public static ExceptionalResponse<HttpServer> start(
            final InetSocketAddress bind,
            final IndexStore store,
            final CatalogFetcher fetcher) {
        Objects.requireNonNull(bind, "bind");
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(fetcher, "fetcher");
        return ExceptionalSupplier.of(() -> {
            final HttpServer server = HttpServer.create(bind, 0);
            server.createContext("/health", exchange -> writeJson(exchange, 200, health(store)));
            server.createContext("/catalogs", exchange -> addCatalog(exchange, store, fetcher));
            server.setExecutor(null);
            server.start();
            return server;
        }).execute();
    }

    private static void addCatalog(
            final HttpExchange exchange,
            final IndexStore store,
            final CatalogFetcher fetcher) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            writeJson(exchange, 405, error("POST required"));
            return;
        }
        final byte[] raw = exchange.getRequestBody().readAllBytes();
        final ExceptionalResponse<Gav> parsed = parseGav(raw);
        if (parsed.wasError()) {
            writeJson(exchange, 400, error("Body must be JSON {groupId, artifactId, version}"));
            return;
        }
        final ExceptionalResponse<CatalogIngest.Outcome> added = CatalogIngest.add(store, parsed.response(), fetcher);
        if (added.wasError()) {
            writeJson(exchange, 500, error("Failed to add catalog"));
            return;
        }
        final CatalogIngest.Outcome outcome = added.response();
        if (outcome.failed()) {
            final int status = outcome.message().startsWith("Missing classifier") ? 404 : 409;
            writeJson(exchange, status, error(outcome.message()));
            return;
        }
        final Map<String, Object> body = health(store);
        body.put("added", parsed.response().compact());
        writeJson(exchange, 200, body);
    }

    private static ExceptionalResponse<Gav> parseGav(final byte[] raw) {
        return ExceptionalSupplier.of(() -> {
            final JsonNode node = JSON.readTree(raw);
            return new Gav(
                    text(node, "groupId"),
                    text(node, "artifactId"),
                    text(node, "version"));
        }).execute();
    }

    private static String text(final JsonNode node, final String field) {
        final JsonNode value = node.get(field);
        if (Objects.isNull(value) || !value.isTextual()) {
            throw new IllegalArgumentException(field);
        }
        return value.asText();
    }

    private static Map<String, Object> health(final IndexStore store) {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("dataDir", store.dataDir().toString());
        body.put("catalogs", store.catalogCount());
        body.put("intents", store.index().intents().size());
        return body;
    }

    private static Map<String, Object> error(final String message) {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", false);
        body.put("error", message);
        return body;
    }

    private static void writeJson(
            final HttpExchange exchange,
            final int status,
            final Map<String, Object> body) throws IOException {
        final byte[] bytes = JSON.writeValueAsBytes(body);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
