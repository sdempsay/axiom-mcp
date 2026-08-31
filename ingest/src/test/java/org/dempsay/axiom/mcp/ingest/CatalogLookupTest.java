package org.dempsay.axiom.mcp.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import org.dempsay.utils.exceptional.api.ExceptionalResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Lookup, search, and get against a stored catalog.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
class CatalogLookupTest {

    @TempDir
    private Path temp;

    @Test
    void matchesTriggerPhrase() throws Exception {
        final IndexStore store = openWithCatalog();
        final CatalogLookup.Result result = CatalogLookup.lookup(store, "IOException", null, 3);
        assertTrue(result.ok());
        assertEquals(1, result.hits().size());
        assertEquals("external_failure", result.hits().get(0).id());
        assertTrue(result.hits().get(0).snippet().contains("ExceptionalSupplier.of"));
        assertTrue(result.hits().get(0).secondarySnippets().get(0).contains("chain"));
    }

    @Test
    void respectsLanguageFilter() throws Exception {
        final IndexStore store = openWithCatalog();
        final CatalogLookup.Result javaHits = CatalogLookup.lookup(store, "IOException", "java", 3);
        assertEquals(1, javaHits.hits().size());
        final CatalogLookup.Result pythonHits = CatalogLookup.lookup(store, "IOException", "python", 3);
        assertTrue(pythonHits.hits().isEmpty());
        assertEquals(CatalogLookup.EMPTY_HINT, pythonHits.hint());
    }

    @Test
    void emptyResultHasGapHint() throws Exception {
        final IndexStore store = openWithCatalog();
        final CatalogLookup.Result result = CatalogLookup.lookup(store, "decode a hex dump", null, 3);
        assertTrue(result.ok());
        assertTrue(result.hits().isEmpty());
        assertEquals(CatalogLookup.EMPTY_HINT, result.hint());
    }

    @Test
    void aliasResolvesViaGet() throws Exception {
        final IndexStore store = openWithCatalog();
        final CatalogLookup.Result result = CatalogLookup.get(store, "file_read_optional");
        assertTrue(result.ok());
        assertEquals("file_to_optional_string", result.hits().get(0).id());
        final CatalogLookup.Result missing = CatalogLookup.get(store, "no_such_intent");
        assertFalse(missing.ok());
        assertTrue(missing.error().contains("Unknown"));
    }

    @Test
    void searchOmitsSnippetUnlessAsked() throws Exception {
        final IndexStore store = openWithCatalog();
        final CatalogLookup.Result hidden = CatalogLookup.search(store, "optional", 10, false);
        assertFalse(hidden.hits().isEmpty());
        assertTrue(hidden.hits().stream().allMatch(hit -> Objects.isNull(hit.snippet())));
        final CatalogLookup.Result shown = CatalogLookup.search(store, "optional", 10, true);
        assertTrue(shown.hits().get(0).snippet().contains("readString"));
    }

    private IndexStore openWithCatalog() throws Exception {
        final Path data = temp.resolve("data");
        final Path dir = data.resolve("catalogs").resolve("org.dempsay.utils").resolve("exceptional");
        Files.createDirectories(dir.resolve("exceptional"));
        Files.writeString(dir.resolve("exceptional").resolve("snippet.java"),
                "ExceptionalSupplier.of(() -> read()).execute();");
        Files.writeString(dir.resolve("exceptional").resolve("chain.java"), "response.chain((l, v) -> v);");
        Files.writeString(dir.resolve("exceptional").resolve("read.java"), "Files.readString(path);");
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
                    snippets:
                      - ref: exceptional/snippet.java
                        role: primary
                      - ref: exceptional/chain.java
                        role: secondary
                    triggers:
                      - IOException
                    never:
                      - programming errors
                    antiPatterns:
                      - pattern: "catch\\\\s*\\\\(\\\\s*IOException"
                        message: Use ExceptionalSupplier
                  - id: file_to_optional_string
                    title: Read file to optional string
                    severity: available
                    language: java
                    blessed:
                      symbol: org.dempsay.utils.files.MoreFiles.readString
                      kind: method
                    snippetRef: exceptional/read.java
                    triggers:
                      - optional string
                    supersedes:
                      - file_read_optional
                """);
        final ExceptionalResponse<IndexStore> opened = IndexStore.open(data);
        assertFalse(opened.wasError());
        return opened.response();
    }
}
