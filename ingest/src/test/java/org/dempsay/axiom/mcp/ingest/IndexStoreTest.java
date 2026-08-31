package org.dempsay.axiom.mcp.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.dempsay.utils.exceptional.api.ExceptionalResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Local file store: create dir, empty is valid, reload from disk.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 0.1.0
 */
class IndexStoreTest {

    @TempDir
    private Path temp;

    @Test
    void emptyStoreIsValid() {
        final Path data = temp.resolve("empty");
        final ExceptionalResponse<IndexStore> opened = IndexStore.open(data);
        assertFalse(opened.wasError());
        assertTrue(Files.isDirectory(data.resolve("catalogs")));
        assertEquals(0, opened.response().catalogCount());
        assertTrue(opened.response().index().intents().isEmpty());
        assertTrue(Files.isRegularFile(data.resolve("index.yaml")));
    }

    @Test
    void createsDataDirIfMissing() {
        final Path data = temp.resolve("missing").resolve("axiom");
        assertFalse(Files.exists(data));
        final ExceptionalResponse<IndexStore> opened = IndexStore.open(data);
        assertFalse(opened.wasError());
        assertTrue(Files.isDirectory(data));
    }

    @Test
    void reloadReadsCatalogsFromDisk() throws Exception {
        final Path data = temp.resolve("store");
        final ExceptionalResponse<IndexStore> opened = IndexStore.open(data);
        assertFalse(opened.wasError());
        assertEquals(0, opened.response().index().intents().size());
        writeCatalog(data);
        final ExceptionalResponse<CatalogIndex> reloaded = opened.response().reload();
        assertFalse(reloaded.wasError(), "reload failed");
        assertEquals(1, reloaded.response().intents().size());
        assertEquals("external_failure", reloaded.response().intents().get(0).id());
        final ExceptionalResponse<IndexStore> restarted = IndexStore.open(data);
        assertFalse(restarted.wasError());
        assertEquals(1, restarted.response().index().intents().size());
    }

    @Test
    void mergeConflictFailsOpen() throws Exception {
        final Path data = temp.resolve("conflict");
        Files.createDirectories(data.resolve("catalogs"));
        writeCatalog(data);
        writeConflictingCatalog(data);
        final ExceptionalResponse<IndexStore> opened = IndexStore.open(data);
        assertTrue(opened.wasError());
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

    private static void writeConflictingCatalog(final Path data) throws Exception {
        final Path dir = data.resolve("catalogs").resolve("org.example").resolve("other");
        Files.createDirectories(dir.resolve("other"));
        Files.writeString(dir.resolve("other").resolve("snippet.java"), "other();");
        Files.writeString(dir.resolve("1.0.0.yaml"), """
                schemaVersion: 1
                artifact:
                  groupId: org.example
                  artifactId: other
                  version: "1.0.0"
                owner:
                  repo: example/other
                intents:
                  - id: external_failure
                    title: Other failure
                    severity: required
                    language: java
                    blessed:
                      symbol: org.example.Other.handle
                      kind: method
                    snippetRef: other/snippet.java
                    triggers:
                      - IOException
                """);
    }
}
