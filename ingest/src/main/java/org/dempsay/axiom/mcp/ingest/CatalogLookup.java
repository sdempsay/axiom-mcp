package org.dempsay.axiom.mcp.ingest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.dempsay.axiom.model.AntiPattern;
import org.dempsay.axiom.model.Artifact;
import org.dempsay.axiom.model.Blessed;
import org.dempsay.axiom.model.Intent;
import org.dempsay.axiom.model.Snippet;
import org.dempsay.axiom.model.SnippetRole;
import org.dempsay.utils.exceptional.api.ExceptionalResponse;
import org.dempsay.utils.exceptional.api.ExceptionalSupplier;

/**
 * Lookup, search, and get against the merged live index.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public final class CatalogLookup {

    /**
     * Hint when lookup/search returns no hits.
     */
    public static final String EMPTY_HINT = "empty result; try catalog_search or catalog_gap";

    /**
     * Tool description: look up before writing helpers.
     */
    public static final String LOOKUP_DESCRIPTION =
            "Call catalog_lookup before writing a helper, try/catch for I/O, or a new *Util class.";

    /**
     * Search tool description.
     */
    public static final String SEARCH_DESCRIPTION =
            "Broader catalog search. Call catalog_lookup first before writing a helper, "
                    + "try/catch for I/O, or a new *Util class.";

    /**
     * Get tool description.
     */
    public static final String GET_DESCRIPTION =
            "Exact catalog get by intent id or alias. Call catalog_lookup before writing a helper, "
                    + "try/catch for I/O, or a new *Util class.";

    private static final int LOOKUP_DEFAULT = 3;

    private static final int SEARCH_DEFAULT = 10;

    private static final int MAX_LIMIT = 50;

    private CatalogLookup() { }

    /**
     * One ranked hit.
     *
     * @param id intent id
     * @param title title
     * @param severity required or available
     * @param language language
     * @param blessed blessed symbol
     * @param gav producing GAV
     * @param snippet primary snippet text, may be null
     * @param secondarySnippets secondary snippet texts
     * @param antiPatterns anti-patterns
     * @param never never-do guidance
     * @param triggers lookup triggers
     * @param score ranking score
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    public record Hit(
            String id,
            String title,
            String severity,
            String language,
            Blessed blessed,
            String gav,
            String snippet,
            List<String> secondarySnippets,
            List<AntiPattern> antiPatterns,
            List<String> never,
            List<String> triggers,
            int score) {
    }

    /**
     * Lookup/search/get payload.
     *
     * @param ok true when the call succeeded (empty hits are ok)
     * @param error error text when not ok
     * @param hits ranked hits
     * @param hint empty-result hint
     * @param nearby nearby ids when get misses
     */
    public record Result(boolean ok, String error, List<Hit> hits, String hint, List<Hit> nearby) {
    }

    /**
     * Matches query against id, title, triggers, and blessed.symbol.
     *
     * @param store live store
     * @param query job text
     * @param language optional language filter
     * @param limit max hits, default 3
     * @return hits, or empty with a gap hint
     */
    public static Result lookup(
            final IndexStore store,
            final String query,
            final String language,
            final int limit) {
        refresh(store);
        return rank(store, query, language, clamp(limit, LOOKUP_DEFAULT), true, false);
    }

    /**
     * Broader ranking than lookup. Snippets omitted unless {@code includeSnippet}.
     *
     * @param store live store
     * @param query search text
     * @param limit max hits, default 10
     * @param includeSnippet include snippet text
     * @return hits, or empty with a gap hint
     */
    public static Result search(
            final IndexStore store,
            final String query,
            final int limit,
            final boolean includeSnippet) {
        refresh(store);
        return rank(store, query, null, clamp(limit, SEARCH_DEFAULT), includeSnippet, true);
    }

    /**
     * Exact id or alias. Unknown id is an error with nearby search hits.
     *
     * @param store live store
     * @param id intent id or alias
     * @return one hit, or an error
     */
    public static Result get(final IndexStore store, final String id) {
        Objects.requireNonNull(store, "store");
        refresh(store);
        if (Objects.isNull(id) || id.isBlank()) {
            return new Result(false, "id is required", List.of(), null, List.of());
        }
        final String resolved = store.index().aliases().getOrDefault(id, id);
        for (final IndexedIntent live : store.index().intents()) {
            if (resolved.equals(live.id())) {
                final Hit hit = toHit(store, live, true);
                if (Objects.nonNull(hit)) {
                    return new Result(true, null, List.of(hit), null, List.of());
                }
            }
        }
        final Result nearby = search(store, id, 5, false);
        return new Result(false, "Unknown intent id '" + id + "'", List.of(), null, nearby.hits());
    }

    private static void refresh(final IndexStore store) {
        store.reload();
    }

    private static Result rank(
            final IndexStore store,
            final String query,
            final String language,
            final int limit,
            final boolean includeSnippet,
            final boolean broad) {
        Objects.requireNonNull(store, "store");
        final String q = Objects.nonNull(query) ? query.trim() : "";
        if (q.isEmpty()) {
            return new Result(true, null, List.of(), EMPTY_HINT, List.of());
        }
        final List<Scored> scored = new ArrayList<>();
        for (final IndexedIntent live : store.index().intents()) {
            if (!languageOk(live.language(), language)) {
                continue;
            }
            final Intent intent = intentOf(store, live);
            if (Objects.isNull(intent)) {
                continue;
            }
            final int score = score(q, intent, broad);
            if (score > 0) {
                scored.add(new Scored(live, score));
            }
        }
        scored.sort(Comparator.comparingInt(Scored::score).reversed().thenComparing(s -> s.live().id()));
        final List<Hit> hits = new ArrayList<>();
        for (final Scored item : scored) {
            if (hits.size() >= limit) {
                break;
            }
            final Hit hit = toHit(store, item.live(), includeSnippet);
            if (Objects.nonNull(hit)) {
                hits.add(new Hit(
                        hit.id(),
                        hit.title(),
                        hit.severity(),
                        hit.language(),
                        hit.blessed(),
                        hit.gav(),
                        hit.snippet(),
                        hit.secondarySnippets(),
                        hit.antiPatterns(),
                        hit.never(),
                        hit.triggers(),
                        item.score()));
            }
        }
        final String hint = hits.isEmpty() ? EMPTY_HINT : null;
        return new Result(true, null, List.copyOf(hits), hint, List.of());
    }

    private static boolean languageOk(final String intentLanguage, final String filter) {
        if (Objects.isNull(filter) || filter.isBlank()) {
            return true;
        }
        return Objects.nonNull(intentLanguage) && intentLanguage.equalsIgnoreCase(filter);
    }

    private static int score(final String query, final Intent intent, final boolean broad) {
        final String q = query.toLowerCase(Locale.ROOT);
        int score = 0;
        if (field(intent.id()).equals(q.replace(' ', '_')) || field(intent.id()).equals(q.replace(' ', '-'))) {
            score += 20;
        }
        if (contains(intent.title(), q) || contains(intent.id(), q)) {
            score += 12;
        }
        if (Objects.nonNull(intent.triggers())) {
            for (final String trigger : intent.triggers()) {
                if (field(trigger).equals(q)) {
                    score += 16;
                } else if (contains(trigger, q)) {
                    score += 10;
                }
            }
        }
        if (Objects.nonNull(intent.blessed()) && contains(intent.blessed().symbol(), q)) {
            score += 8;
        }
        for (final String token : tokens(q)) {
            if (token.length() < 2) {
                continue;
            }
            if (contains(intent.id(), token)) {
                score += 3;
            }
            if (contains(intent.title(), token)) {
                score += 2;
            }
            if (Objects.nonNull(intent.triggers())) {
                for (final String trigger : intent.triggers()) {
                    if (contains(trigger, token)) {
                        score += 2;
                    } else if (broad && prefix(trigger, token)) {
                        score += 2;
                    }
                }
            }
            if (Objects.nonNull(intent.blessed()) && contains(intent.blessed().symbol(), token)) {
                score += 2;
            }
        }
        return score;
    }

    private static Hit toHit(final IndexStore store, final IndexedIntent live, final boolean includeSnippet) {
        final StoredCatalog stored = source(store, live);
        if (Objects.isNull(stored)) {
            return null;
        }
        final Intent intent = intentOf(stored, live.id());
        if (Objects.isNull(intent)) {
            return null;
        }
        String primary = null;
        List<String> secondary = List.of();
        if (includeSnippet) {
            primary = readSnippet(stored.yamlFile().getParent(), intent.snippetRef());
            secondary = secondarySnippets(stored.yamlFile().getParent(), intent);
        }
        final Artifact artifact = live.artifact();
        final String gav = artifact.groupId() + ":" + artifact.artifactId() + ":" + artifact.version();
        final String severity = Objects.nonNull(live.severity()) ? live.severity().name().toLowerCase(Locale.ROOT) : null;
        return new Hit(
                live.id(),
                live.title(),
                severity,
                live.language(),
                live.blessed(),
                gav,
                primary,
                secondary,
                Objects.nonNull(intent.antiPatterns()) ? intent.antiPatterns() : List.of(),
                Objects.nonNull(intent.never()) ? intent.never() : List.of(),
                Objects.nonNull(intent.triggers()) ? intent.triggers() : List.of(),
                0);
    }

    private static List<String> secondarySnippets(final Path catalogDir, final Intent intent) {
        if (Objects.isNull(intent.snippets())) {
            return List.of();
        }
        final List<String> texts = new ArrayList<>();
        for (final Snippet snippet : intent.snippets()) {
            if (snippet.role() != SnippetRole.SECONDARY) {
                continue;
            }
            final String text = readSnippet(catalogDir, snippet.ref());
            if (Objects.nonNull(text)) {
                texts.add(text);
            }
        }
        return List.copyOf(texts);
    }

    private static String readSnippet(final Path catalogDir, final String ref) {
        if (Objects.isNull(catalogDir) || Objects.isNull(ref) || ref.isBlank()) {
            return null;
        }
        final Path dir = catalogDir.toAbsolutePath().normalize();
        final Path file = dir.resolve(ref).normalize();
        if (!file.startsWith(dir)) {
            return null;
        }
        final ExceptionalResponse<String> read = ExceptionalSupplier.of(() -> Files.readString(file)).execute();
        if (read.wasError()) {
            return null;
        }
        return read.response();
    }

    private static StoredCatalog source(final IndexStore store, final IndexedIntent live) {
        for (final StoredCatalog stored : store.stored()) {
            final Artifact artifact = stored.catalog().artifact();
            if (Objects.equals(artifact.groupId(), live.artifact().groupId())
                    && Objects.equals(artifact.artifactId(), live.artifact().artifactId())
                    && Objects.equals(artifact.version(), live.artifact().version())) {
                return stored;
            }
        }
        return null;
    }

    private static Intent intentOf(final IndexStore store, final IndexedIntent live) {
        final StoredCatalog stored = source(store, live);
        if (Objects.isNull(stored)) {
            return null;
        }
        return intentOf(stored, live.id());
    }

    private static Intent intentOf(final StoredCatalog stored, final String id) {
        if (Objects.isNull(stored.catalog().intents())) {
            return null;
        }
        for (final Intent intent : stored.catalog().intents()) {
            if (id.equals(intent.id())) {
                return intent;
            }
        }
        return null;
    }

    private static int clamp(final int limit, final int fallback) {
        if (limit <= 0) {
            return fallback;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private static List<String> tokens(final String query) {
        return List.of(query.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"));
    }

    private static String field(final String value) {
        return Objects.nonNull(value) ? value.toLowerCase(Locale.ROOT) : "";
    }

    private static boolean contains(final String value, final String needle) {
        return field(value).contains(needle.toLowerCase(Locale.ROOT));
    }

    private static boolean prefix(final String value, final String token) {
        for (final String part : tokens(field(value))) {
            if (part.startsWith(token)) {
                return true;
            }
            if (token.startsWith(part) && part.length() >= 2) {
                return true;
            }
        }
        return false;
    }

    private record Scored(IndexedIntent live, int score) {
    }
}
