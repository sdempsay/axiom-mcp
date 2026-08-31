package org.dempsay.axiom.mcp.ingest;

import java.util.Objects;

/**
 * Maven coordinates used to add a catalog: {@code groupId:artifactId:version}.
 *
 * @param groupId Maven groupId
 * @param artifactId Maven artifactId
 * @param version Maven version
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public record Gav(String groupId, String artifactId, String version) {

    /**
     * @param groupId Maven groupId
     * @param artifactId Maven artifactId
     * @param version Maven version
     */
    public Gav {
        Objects.requireNonNull(groupId, "groupId");
        Objects.requireNonNull(artifactId, "artifactId");
        Objects.requireNonNull(version, "version");
        if (groupId.isBlank() || artifactId.isBlank() || version.isBlank()) {
            throw new IllegalArgumentException("GAV parts must be non-blank");
        }
    }

    /**
     * Parses {@code groupId:artifactId:version}.
     *
     * @param value colon-separated GAV
     * @return parsed GAV
     */
    public static Gav parse(final String value) {
        Objects.requireNonNull(value, "value");
        final String[] parts = value.split(":", 3);
        if (parts.length != 3) {
            throw new IllegalArgumentException("GAV must be groupId:artifactId:version");
        }
        return new Gav(parts[0], parts[1], parts[2]);
    }

    /**
     * @return {@code groupId:artifactId:version}
     */
    public String compact() {
        return groupId + ":" + artifactId + ":" + version;
    }
}
