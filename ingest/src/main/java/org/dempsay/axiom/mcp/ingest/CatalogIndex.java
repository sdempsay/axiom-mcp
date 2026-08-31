package org.dempsay.axiom.mcp.ingest;

import java.util.List;
import java.util.Map;

/**
 * Merged live index written to {@code index.yaml}.
 *
 * @param schemaVersion always 1
 * @param generatedAt ISO-8601 timestamp
 * @param intents sorted by id
 * @param aliases superseded id to current id
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 0.1.0
 */
public record CatalogIndex(
        int schemaVersion,
        String generatedAt,
        List<IndexedIntent> intents,
        Map<String, String> aliases) {
}
