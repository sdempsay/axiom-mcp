package org.dempsay.axiom.mcp.ingest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * File-based Maven repository for ingest tests.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
final class TestMavenRepo {

    private TestMavenRepo() { }

    static CatalogFetcher fetcher(final Path remote, final Path local) {
        return new CatalogFetcher(local, List.of(remote.toUri().toString()));
    }

    static void publish(
            final Path remote,
            final Gav gav,
            final String yaml,
            final Map<String, String> snippets) throws Exception {
        final Path dir = artifactDir(remote, gav);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(fileName(gav, "agent-catalog", "yaml")), yaml);
        if (snippets.isEmpty()) {
            return;
        }
        final Path zip = dir.resolve(fileName(gav, "agent-catalog-examples", "zip"));
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            for (final Map.Entry<String, String> snippet : snippets.entrySet()) {
                out.putNextEntry(new ZipEntry(snippet.getKey()));
                out.write(snippet.getValue().getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
    }

    static String catalogYaml(final String groupId, final String artifactId, final String intentId, final String symbol) {
        return """
                schemaVersion: 1
                artifact:
                  groupId: %s
                  artifactId: %s
                  version: "1.0.0"
                owner:
                  repo: example/lib
                intents:
                  - id: %s
                    title: %s
                    severity: available
                    language: java
                    blessed:
                      symbol: %s
                      kind: method
                    snippetRef: s/snippet.java
                    triggers:
                      - IOException
                """.formatted(groupId, artifactId, intentId, intentId, symbol);
    }

    private static Path artifactDir(final Path remote, final Gav gav) {
        Path dir = remote;
        for (final String part : gav.groupId().split("\\.")) {
            dir = dir.resolve(part);
        }
        return dir.resolve(gav.artifactId()).resolve(gav.version());
    }

    private static String fileName(final Gav gav, final String classifier, final String extension) {
        return gav.artifactId() + "-" + gav.version() + "-" + classifier + "." + extension;
    }
}
