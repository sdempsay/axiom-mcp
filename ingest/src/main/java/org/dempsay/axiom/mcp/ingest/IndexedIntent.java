package org.dempsay.axiom.mcp.ingest;

import org.dempsay.axiom.model.Artifact;
import org.dempsay.axiom.model.Blessed;
import org.dempsay.axiom.model.Severity;

/**
 * One live intent in the merged index, with the producing artifact.
 *
 * @param id intent id
 * @param title title
 * @param severity required or available
 * @param language language
 * @param blessed blessed symbol
 * @param snippetRef snippet path from the source catalog
 * @param artifact producing GAV
 * @param ownerRepo owning repo, may be null
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 0.1.0
 */
public record IndexedIntent(
        String id,
        String title,
        Severity severity,
        String language,
        Blessed blessed,
        String snippetRef,
        Artifact artifact,
        String ownerRepo) {
}
