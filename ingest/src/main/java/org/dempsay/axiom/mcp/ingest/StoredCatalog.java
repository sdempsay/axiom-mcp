package org.dempsay.axiom.mcp.ingest;

import java.nio.file.Path;

import org.dempsay.axiom.model.Catalog;

/**
 * A catalog yaml on disk and its parsed document.
 *
 * @param yamlFile catalog yaml path
 * @param catalog parsed catalog
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public record StoredCatalog(Path yamlFile, Catalog catalog) {
}
