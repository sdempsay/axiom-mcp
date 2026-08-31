package org.dempsay.axiom.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.dempsay.utils.exceptional.api.ExceptionalResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Process start: data dir, empty store, HTTP health, reload after restart.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
class AxiomServerTest {

    @TempDir
    private Path temp;

    @Test
    void emptyStoreHealthOk() throws Exception {
        final Path data = temp.resolve("empty");
        final ServerConfig config = new ServerConfig(data, new InetSocketAddress("127.0.0.1", 0), false, List.of());
        final ExceptionalResponse<AxiomServer> started = AxiomServer.start(config);
        assertFalse(started.wasError());
        final AxiomServer server = started.response();
        try {
            final String body = get(server, "/health");
            assertTrue(body.contains("\"ok\":true"));
            assertTrue(body.contains("\"intents\":0"));
            assertTrue(body.contains("\"catalogs\":0"));
            assertTrue(Files.isDirectory(data.resolve("catalogs")));
        } finally {
            server.stop();
        }
    }

    @Test
    void restartReloadsCatalogsFromDisk() throws Exception {
        final Path data = temp.resolve("reload");
        final ServerConfig config = new ServerConfig(data, new InetSocketAddress("127.0.0.1", 0), false, List.of());
        final ExceptionalResponse<AxiomServer> first = AxiomServer.start(config);
        assertFalse(first.wasError());
        first.response().stop();
        writeCatalog(data);
        final ExceptionalResponse<AxiomServer> second = AxiomServer.start(config);
        assertFalse(second.wasError());
        try {
            assertEquals(1, second.response().store().index().intents().size());
            final String body = get(second.response(), "/health");
            assertTrue(body.contains("\"intents\":1"));
            assertTrue(body.contains("\"catalogs\":1"));
        } finally {
            second.response().stop();
        }
    }

    @Test
    void lookupHttpMatchesTrigger() throws Exception {
        final Path data = temp.resolve("lookup");
        writeCatalog(data);
        final ServerConfig config = new ServerConfig(data, new InetSocketAddress("127.0.0.1", 0), false, List.of());
        final ExceptionalResponse<AxiomServer> started = AxiomServer.start(config);
        assertFalse(started.wasError());
        final AxiomServer server = started.response();
        try {
            final String body = get(server, "/lookup?q=IOException&language=java");
            assertTrue(body.contains("\"ok\":true"));
            assertTrue(body.contains("external_failure"));
            assertTrue(body.contains("of().execute();"));
            final String missing = get(server, "/get?id=nope");
            assertTrue(missing.contains("\"ok\":false"));
            assertTrue(missing.contains("Unknown"));
        } finally {
            server.stop();
        }
    }

    @Test
    void parseBindAndData() {
        final ServerConfig config = ServerConfig.parse(new String[] {
                "--data", "/tmp/axiom-test",
                "--bind", "127.0.0.1:0",
                "--stdio",
                "--repo", "https://maven.pkg.github.com/sdempsay/*"
        });
        assertEquals(Path.of("/tmp/axiom-test"), config.dataDir());
        assertEquals("127.0.0.1", config.bind().getHostString());
        assertEquals(0, config.bind().getPort());
        assertTrue(config.stdio());
        assertTrue(config.repos().contains("https://maven.pkg.github.com/sdempsay/*"));
    }

    private static String get(final AxiomServer server, final String path) throws Exception {
        final int port = server.address().getPort();
        final HttpClient client = HttpClient.newHttpClient();
        final HttpRequest request = HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + path)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    private static void writeCatalog(final Path data) throws Exception {
        final Path dir = data.resolve("catalogs").resolve("org.dempsay.utils").resolve("exceptional");
        Files.createDirectories(dir.resolve("exceptional"));
        Files.writeString(dir.resolve("exceptional").resolve("snippet.java"), "of().execute();");
        Files.writeString(dir.resolve("1.0.9.yaml"), """
                schemaVersion: 1
                artifact:
                  groupId: org.dempsay.utils
                  artifactId: exceptional
                  version: "1.0.9"
                owner:
                  repo: sdempsay/exceptional-java
                intents:
                  - id: external_failure
                    title: Handle external failure
                    severity: required
                    language: java
                    blessed:
                      symbol: org.dempsay.utils.exceptional.api.ExceptionalSupplier.of
                      kind: method
                    snippetRef: exceptional/snippet.java
                    triggers:
                      - IOException
                """);
    }
}
