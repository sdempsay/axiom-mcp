package org.dempsay.axiom.mcp;

import java.net.InetSocketAddress;
import java.util.Objects;

import com.sun.net.httpserver.HttpServer;

import org.dempsay.axiom.mcp.ingest.CatalogFetcher;
import org.dempsay.axiom.mcp.ingest.IndexStore;
import org.dempsay.utils.exceptional.api.ExceptionalResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Live catalog process: HTTP plus optional stdio MCP, one JVM, local files.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
@SuppressWarnings("checkstyle:IllegalImport")
public final class AxiomServer {

    private static final Logger LOG = LoggerFactory.getLogger(AxiomServer.class);

    private final IndexStore store;
    private final HttpServer http;

    private AxiomServer(final IndexStore store, final HttpServer http) {
        this.store = store;
        this.http = http;
    }

    /**
     * @param args {@code --data}, {@code --bind host:port}, {@code --stdio}, {@code --repo URL}
     */
    public static void main(final String[] args) {
        final ServerConfig config = ServerConfig.parse(args);
        if (CatalogAdd.requested(args)) {
            System.exit(CatalogAdd.run(args, CatalogFetcher.standard(config.repos())));
            return;
        }
        final ExceptionalResponse<AxiomServer> started = start(config);
        if (started.wasError()) {
            System.err.println("axiom-mcp failed to start (data dir unwritable or catalogs invalid)");
            System.exit(1);
            return;
        }
        final AxiomServer server = started.response();
        LOG.info("axiom-mcp HTTP {} intents={} data={}",
                server.address(),
                server.store().index().intents().size(),
                server.store().dataDir());
        if (config.stdio()) {
            McpStdio.serve(server.store());
        }
    }

    /**
     * Opens the store and binds HTTP.
     *
     * @param config process config
     * @return running server
     */
    public static ExceptionalResponse<AxiomServer> start(final ServerConfig config) {
        Objects.requireNonNull(config, "config");
        return start(config, CatalogFetcher.standard(config.repos()));
    }

    /**
     * Opens the store and binds HTTP using {@code fetcher} for {@code POST /catalogs}.
     *
     * @param config process config
     * @param fetcher Maven Resolver fetch
     * @return running server
     */
    public static ExceptionalResponse<AxiomServer> start(final ServerConfig config, final CatalogFetcher fetcher) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(fetcher, "fetcher");
        return IndexStore.open(config.dataDir())
                .chain((listener, store) -> HttpApi.start(config.bind(), store, fetcher)
                        .then(http -> new AxiomServer(store, http)));
    }

    /**
     * @return bound address (port may be ephemeral)
     */
    public InetSocketAddress address() {
        return http.getAddress();
    }

    /**
     * @return open store
     */
    public IndexStore store() {
        return store;
    }

    /**
     * Stops HTTP.
     */
    public void stop() {
        http.stop(0);
    }
}
