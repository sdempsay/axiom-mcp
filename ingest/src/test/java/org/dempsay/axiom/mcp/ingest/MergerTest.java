package org.dempsay.axiom.mcp.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.dempsay.axiom.model.Artifact;
import org.dempsay.axiom.model.Blessed;
import org.dempsay.axiom.model.BlessedKind;
import org.dempsay.axiom.model.Catalog;
import org.dempsay.axiom.model.Intent;
import org.dempsay.axiom.model.Owner;
import org.dempsay.axiom.model.Severity;
import org.junit.jupiter.api.Test;

/**
 * C3 merge rules.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
class MergerTest {

    @Test
    void mergesDistinctIds() {
        final Catalog first = catalog("a", "one", "org.example.A.one");
        final Catalog second = catalog("b", "two", "org.example.B.two");
        final Merger.Outcome outcome = Merger.merge(List.of(first, second));
        assertFalse(outcome.failed());
        assertEquals(2, outcome.index().intents().size());
        assertEquals("a", outcome.index().intents().get(0).id());
        assertEquals("b", outcome.index().intents().get(1).id());
    }

    @Test
    void duplicateSymbolConflictFails() {
        final Catalog first = catalog("same", "one", "org.example.A.one");
        final Catalog second = catalog("same", "two", "org.example.B.other");
        final Merger.Outcome outcome = Merger.merge(List.of(first, second));
        assertTrue(outcome.failed());
        assertTrue(outcome.message().contains("same"));
        assertTrue(outcome.message().contains("lib-one"));
        assertTrue(outcome.message().contains("lib-two"));
    }

    @Test
    void supersedesWritesAlias() {
        final Intent intent = new Intent(
                "file_to_optional_string",
                "Read file",
                Severity.AVAILABLE,
                "java",
                new Blessed("org.example.Files.read", BlessedKind.METHOD),
                "s.java",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("file_read_optional"));
        final Catalog catalog = new Catalog(
                1,
                new Artifact("org.example", "lib", "1.0"),
                new Owner("ex/lib", null),
                List.of(intent));
        final Merger.Outcome outcome = Merger.merge(List.of(catalog));
        assertFalse(outcome.failed());
        assertEquals("file_to_optional_string", outcome.index().aliases().get("file_read_optional"));
    }

    @Test
    void removeDropsIntents() {
        final Catalog first = catalog("a", "one", "org.example.A.one");
        final Catalog second = catalog("b", "two", "org.example.B.two");
        final Merger.Outcome both = Merger.merge(List.of(first, second));
        assertEquals(2, both.index().intents().size());
        final Merger.Outcome after = Merger.merge(List.of(first));
        assertEquals(1, after.index().intents().size());
        assertEquals("a", after.index().intents().get(0).id());
    }

    private static Catalog catalog(final String id, final String artifactId, final String symbol) {
        final Intent intent = new Intent(
                id,
                id,
                Severity.AVAILABLE,
                "java",
                new Blessed(symbol, BlessedKind.METHOD),
                "s.java",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
        return new Catalog(
                1,
                new Artifact("org.example", "lib-" + artifactId, "1.0"),
                new Owner("ex/" + artifactId, null),
                List.of(intent));
    }
}
