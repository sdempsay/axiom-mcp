package org.dempsay.axiom.mcp;

import java.util.Objects;

import org.dempsay.axiom.mcp.ingest.CatalogFetcher;
import org.dempsay.axiom.mcp.ingest.CatalogIngest;
import org.dempsay.axiom.mcp.ingest.Gav;
import org.dempsay.axiom.mcp.ingest.IndexStore;
import org.dempsay.utils.exceptional.api.ExceptionalResponse;
import org.dempsay.utils.exceptional.api.ExceptionalSupplier;

/**
 * One-shot {@code catalog add groupId:artifactId:version}. Full CLI is C5.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public final class CatalogAdd {

    private CatalogAdd() { }

    /**
     * @param args command line
     * @return true when {@code catalog add} was requested
     */
    public static boolean requested(final String[] args) {
        return Objects.nonNull(args)
                && args.length >= 2
                && "catalog".equals(args[0])
                && "add".equals(args[1]);
    }

    /**
     * Adds one catalog and prints the result. Does not start HTTP.
     *
     * @param args {@code catalog add g:a:v} plus {@code --data}
     * @param fetcher Maven Resolver fetch
     * @return process exit code
     */
    public static int run(final String[] args, final CatalogFetcher fetcher) {
        Objects.requireNonNull(args, "args");
        Objects.requireNonNull(fetcher, "fetcher");
        if (args.length < 3) {
            System.err.println("usage: axiom catalog add groupId:artifactId:version [--data DIR]");
            return 1;
        }
        final ExceptionalResponse<Gav> parsed = ExceptionalSupplier.of(() -> Gav.parse(args[2])).execute();
        if (parsed.wasError()) {
            System.err.println("GAV must be groupId:artifactId:version");
            return 1;
        }
        final ServerConfig config = ServerConfig.parse(args);
        final ExceptionalResponse<IndexStore> opened = IndexStore.open(config.dataDir());
        if (opened.wasError()) {
            System.err.println("Failed to open data dir " + config.dataDir());
            return 1;
        }
        final ExceptionalResponse<CatalogIngest.Outcome> added =
                CatalogIngest.add(opened.response(), parsed.response(), fetcher);
        if (added.wasError()) {
            System.err.println("Failed to add " + parsed.response().compact());
            return 1;
        }
        final CatalogIngest.Outcome outcome = added.response();
        if (outcome.failed()) {
            System.err.println(outcome.message());
            return 1;
        }
        System.out.println(outcome.message());
        return 0;
    }
}
