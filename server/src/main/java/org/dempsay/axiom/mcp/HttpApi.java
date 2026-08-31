package org.dempsay.axiom.mcp;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.dempsay.axiom.mcp.ingest.IndexStore;
import org.dempsay.utils.exceptional.api.ExceptionalResponse;
import org.dempsay.utils.exceptional.api.ExceptionalSupplier;

/**
 * Local HTTP API for the live catalog process.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 0.1.0
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
     * @return running server
     */
    public static ExceptionalResponse<HttpServer> start(final InetSocketAddress bind, final IndexStore store) {
        Objects.requireNonNull(bind, "bind");
        Objects.requireNonNull(store, "store");
        return ExceptionalSupplier.of(() -> {
            final HttpServer server = HttpServer.create(bind, 0);
            server.createContext("/health", exchange -> writeJson(exchange, health(store)));
            server.setExecutor(null);
            server.start();
            return server;
        }).execute();
    }

    private static Map<String, Object> health(final IndexStore store) {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("dataDir", store.dataDir().toString());
        body.put("catalogs", store.catalogCount());
        body.put("intents", store.index().intents().size());
        return body;
    }

    private static void writeJson(final HttpExchange exchange, final Map<String, Object> body) throws IOException {
        final byte[] bytes = JSON.writeValueAsBytes(body);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
