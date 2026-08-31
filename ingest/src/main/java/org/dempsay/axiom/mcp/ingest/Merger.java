package org.dempsay.axiom.mcp.ingest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.dempsay.axiom.model.Blessed;
import org.dempsay.axiom.model.Catalog;
import org.dempsay.axiom.model.Intent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Merges catalog documents into one live index. Duplicate intent ids with different
 * blessed symbols fail.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 0.1.0
 */
public final class Merger {

    private static final Logger LOG = LoggerFactory.getLogger(Merger.class);

    private Merger() { }

    /**
     * Outcome of a merge.
     *
     * @param failed true when a duplicate-id conflict occurred
     * @param message error text when failed
     * @param index merged index when successful
     */
    public record Outcome(boolean failed, String message, CatalogIndex index) {
    }

    /**
     * Merges {@code catalogs} into a deterministic index (except {@code generatedAt}).
     *
     * @param catalogs catalogs from disk
     * @return merge outcome
     */
    public static Outcome merge(final List<Catalog> catalogs) {
        Objects.requireNonNull(catalogs, "catalogs");
        final Map<String, IndexedIntent> byId = new LinkedHashMap<>();
        final Map<String, String> aliases = new LinkedHashMap<>();
        for (final Catalog catalog : catalogs) {
            if (Objects.isNull(catalog) || Objects.isNull(catalog.intents())) {
                continue;
            }
            for (final Intent intent : catalog.intents()) {
                final Outcome conflict = putIntent(byId, catalog, intent);
                if (conflict.failed()) {
                    return conflict;
                }
                addAliases(aliases, intent);
            }
        }
        final List<IndexedIntent> sorted = new ArrayList<>(byId.values());
        sorted.sort(Comparator.comparing(IndexedIntent::id));
        return new Outcome(
                false,
                null,
                new CatalogIndex(1, Instant.now().toString(), List.copyOf(sorted), Map.copyOf(aliases)));
    }

    private static Outcome putIntent(
            final Map<String, IndexedIntent> byId,
            final Catalog catalog,
            final Intent intent) {
        final IndexedIntent incoming = toIndexed(catalog, intent);
        final IndexedIntent existing = byId.get(intent.id());
        if (Objects.isNull(existing)) {
            byId.put(intent.id(), incoming);
            return new Outcome(false, null, null);
        }
        if (!sameSymbol(existing.blessed(), incoming.blessed())) {
            return new Outcome(
                    true,
                    "Duplicate intent id '" + intent.id() + "' with different blessed symbols: "
                            + gav(existing) + " vs " + gav(incoming),
                    null);
        }
        if (Objects.equals(existing.ownerRepo(), incoming.ownerRepo())) {
            LOG.warn("Duplicate intent id {} from {}; keeping first", intent.id(), gav(incoming));
        }
        return new Outcome(false, null, null);
    }

    private static void addAliases(final Map<String, String> aliases, final Intent intent) {
        if (Objects.isNull(intent.supersedes())) {
            return;
        }
        for (final String previous : intent.supersedes()) {
            aliases.putIfAbsent(previous, intent.id());
        }
    }

    private static IndexedIntent toIndexed(final Catalog catalog, final Intent intent) {
        final String ownerRepo = Objects.nonNull(catalog.owner()) ? catalog.owner().repo() : null;
        return new IndexedIntent(
                intent.id(),
                intent.title(),
                intent.severity(),
                intent.language(),
                intent.blessed(),
                intent.snippetRef(),
                catalog.artifact(),
                ownerRepo);
    }

    private static boolean sameSymbol(final Blessed left, final Blessed right) {
        if (Objects.isNull(left) || Objects.isNull(right)) {
            return Objects.equals(left, right);
        }
        return Objects.equals(left.symbol(), right.symbol());
    }

    private static String gav(final IndexedIntent intent) {
        return intent.artifact().groupId() + ":" + intent.artifact().artifactId() + ":"
                + intent.artifact().version();
    }
}
