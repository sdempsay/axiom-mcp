package org.dempsay.axiom.mcp.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.dempsay.utils.exceptional.api.ExceptionalResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Add by GAV: store, replace, missing classifier, merge conflict does not store.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
class CatalogIngestTest {

    @TempDir
    private Path temp;

    @Test
    void addStoresCatalogAndSnippets() throws Exception {
        final Fixture fixture = fixture();
        final Gav gav = new Gav("org.example", "lib", "1.0.0");
        publish(fixture, gav, "external_failure", "org.example.A.handle");
        final ExceptionalResponse<CatalogIngest.Outcome> added =
                CatalogIngest.add(fixture.store, gav, fixture.fetcher);
        assertFalse(added.wasError());
        assertFalse(added.response().failed(), added.response().message());
        assertEquals(1, added.response().index().intents().size());
        assertTrue(Files.isRegularFile(fixture.store.catalogYaml(gav)));
        assertTrue(Files.isRegularFile(fixture.store.catalogYaml(gav).getParent().resolve("s/snippet.java")));
    }

    @Test
    void reAddReplacesSameGav() throws Exception {
        final Fixture fixture = fixture();
        final Gav gav = new Gav("org.example", "lib", "1.0.0");
        publish(fixture, gav, "external_failure", "org.example.A.handle");
        CatalogIngest.add(fixture.store, gav, fixture.fetcher);
        final Path stored = fixture.store.catalogYaml(gav);
        Files.writeString(stored, "stale");
        final ExceptionalResponse<CatalogIngest.Outcome> replaced =
                CatalogIngest.add(fixture.store, gav, fixture.fetcher);
        assertFalse(replaced.wasError());
        assertFalse(replaced.response().failed(), replaced.response().message());
        assertEquals(1, replaced.response().index().intents().size());
        assertEquals("external_failure", replaced.response().index().intents().get(0).id());
        assertFalse(Files.readString(stored).contains("stale"));
    }

    @Test
    void missingClassifierDoesNotStore() throws Exception {
        final Fixture fixture = fixture();
        final Gav gav = new Gav("org.example", "missing", "1.0.0");
        final ExceptionalResponse<CatalogIngest.Outcome> added =
                CatalogIngest.add(fixture.store, gav, fixture.fetcher);
        assertFalse(added.wasError());
        assertTrue(added.response().failed());
        assertTrue(added.response().message().contains("agent-catalog"));
        assertEquals(0, fixture.store.catalogCount());
    }

    @Test
    void mergeConflictDoesNotStoreNewCatalog() throws Exception {
        final Fixture fixture = fixture();
        final Gav first = new Gav("org.example", "lib-one", "1.0.0");
        final Gav second = new Gav("org.example", "lib-two", "1.0.0");
        publish(fixture, first, "same", "org.example.A.one");
        publish(fixture, second, "same", "org.example.B.other");
        final ExceptionalResponse<CatalogIngest.Outcome> addedFirst =
                CatalogIngest.add(fixture.store, first, fixture.fetcher);
        assertFalse(addedFirst.response().failed(), addedFirst.response().message());
        final ExceptionalResponse<CatalogIngest.Outcome> addedSecond =
                CatalogIngest.add(fixture.store, second, fixture.fetcher);
        assertFalse(addedSecond.wasError());
        assertTrue(addedSecond.response().failed());
        assertTrue(addedSecond.response().message().contains("same"));
        assertFalse(Files.exists(fixture.store.catalogYaml(second)));
        assertEquals(1, fixture.store.catalogCount());
    }

    private Fixture fixture() {
        final Path remote = temp.resolve("remote");
        final Path local = temp.resolve("m2");
        final Path data = temp.resolve("data");
        final ExceptionalResponse<IndexStore> opened = IndexStore.open(data);
        assertFalse(opened.wasError());
        return new Fixture(opened.response(), TestMavenRepo.fetcher(remote, local), remote);
    }

    private static void publish(
            final Fixture fixture,
            final Gav gav,
            final String intentId,
            final String symbol) throws Exception {
        TestMavenRepo.publish(
                fixture.remote,
                gav,
                TestMavenRepo.catalogYaml(gav.groupId(), gav.artifactId(), intentId, symbol),
                Map.of("s/snippet.java", "snippet();"));
    }

    private record Fixture(IndexStore store, CatalogFetcher fetcher, Path remote) {
    }
}
