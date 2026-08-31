package org.dempsay.axiom.mcp.ingest;

import java.util.Objects;

/**
 * One Maven remote used by {@link CatalogFetcher}. Password is omitted from {@link #toString()}.
 *
 * @param id repository id (matches {@code settings.xml} {@code <server>})
 * @param url base URL
 * @param username optional
 * @param password optional
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public record RemoteRepositorySpec(String id, String url, String username, String password) {

    /**
     * @param id repository id
     * @param url base URL
     * @param username optional username
     * @param password optional password
     */
    public RemoteRepositorySpec {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(url, "url");
    }

    /**
     * @return {@code id} at {@code url}, never the password
     */
    @Override
    public String toString() {
        return id + " @ " + url;
    }
}
