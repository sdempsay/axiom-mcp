package org.dempsay.axiom.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.dempsay.axiom.mcp.ingest.CatalogFetcher;
import org.dempsay.axiom.mcp.ingest.Gav;
import org.dempsay.utils.exceptional.api.ExceptionalResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code POST /catalogs} and {@code catalog add g:a:v}.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
class CatalogAddTest {

    @TempDir
    private Path temp;

    @Test
    void postCatalogsAddsByGav() throws Exception {
        final Path remote = temp.resolve("remote");
        final Path local = temp.resolve("m2");
        final Gav gav = new Gav("org.example", "lib", "1.0.0");
        publish(remote, gav);
        final CatalogFetcher fetcher = new CatalogFetcher(local, List.of(remote.toUri().toString()));
        final ServerConfig config = new ServerConfig(
                temp.resolve("data"),
                new InetSocketAddress("127.0.0.1", 0),
                false,
                List.of());
        final ExceptionalResponse<AxiomServer> started = AxiomServer.start(config, fetcher);
        assertFalse(started.wasError());
        final AxiomServer server = started.response();
        try {
            final HttpResponse<String> response = post(server, """
                    {"groupId":"org.example","artifactId":"lib","version":"1.0.0"}
                    """);
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("\"ok\":true"));
            assertTrue(response.body().contains("\"intents\":1"));
            final HttpResponse<String> missing = post(server, """
                    {"groupId":"org.example","artifactId":"nope","version":"1.0.0"}
                    """);
            assertEquals(404, missing.statusCode());
            assertTrue(missing.body().contains("agent-catalog"));
        } finally {
            server.stop();
        }
    }

    @Test
    void catalogAddCommand() throws Exception {
        final Path remote = temp.resolve("remote");
        final Path local = temp.resolve("m2");
        final Path data = temp.resolve("data");
        final Gav gav = new Gav("org.example", "lib", "1.0.0");
        publish(remote, gav);
        final CatalogFetcher fetcher = new CatalogFetcher(local, List.of(remote.toUri().toString()));
        final int code = CatalogAdd.run(new String[] {
                "catalog", "add", "org.example:lib:1.0.0",
                "--data", data.toString()
        }, fetcher);
        assertEquals(0, code);
        assertTrue(Files.isRegularFile(
                data.resolve("catalogs").resolve("org.example").resolve("lib").resolve("1.0.0.yaml")));
        assertTrue(CatalogAdd.requested(new String[] {"catalog", "add", "g:a:v"}));
        assertFalse(CatalogAdd.requested(new String[] {"--stdio"}));
    }

    private static HttpResponse<String> post(final AxiomServer server, final String json) throws Exception {
        final int port = server.address().getPort();
        final HttpClient client = HttpClient.newHttpClient();
        final HttpRequest request = HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + "/catalogs"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static void publish(final Path remote, final Gav gav) throws Exception {
        Path dir = remote;
        for (final String part : gav.groupId().split("\\.")) {
            dir = dir.resolve(part);
        }
        dir = dir.resolve(gav.artifactId()).resolve(gav.version());
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(gav.artifactId() + "-" + gav.version() + "-agent-catalog.yaml"), """
                schemaVersion: 1
                artifact:
                  groupId: org.example
                  artifactId: lib
                  version: "1.0.0"
                owner:
                  repo: example/lib
                intents:
                  - id: external_failure
                    title: Handle failure
                    severity: available
                    language: java
                    blessed:
                      symbol: org.example.A.handle
                      kind: method
                    snippetRef: s/snippet.java
                    triggers:
                      - IOException
                """);
        final Path zip = dir.resolve(gav.artifactId() + "-" + gav.version() + "-agent-catalog-examples.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("s/snippet.java"));
            out.write("snippet();".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
    }
}
