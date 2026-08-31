package org.dempsay.axiom.mcp;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.dempsay.axiom.mcp.ingest.CatalogFetcher;

/**
 * Process flags for {@link AxiomServer}.
 *
 * @param dataDir catalog store
 * @param bind listen address
 * @param stdio if true, also run MCP on stdin/stdout
 * @param repos extra Maven remote URLs ({@code --repo}, {@code AXIOM_REPOS})
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public record ServerConfig(Path dataDir, InetSocketAddress bind, boolean stdio, List<String> repos) {

    /**
     * @param dataDir catalog store
     * @param bind listen address
     * @param stdio stdio MCP
     * @param repos extra remotes
     */
    public ServerConfig {
        repos = List.copyOf(Objects.requireNonNullElse(repos, List.of()));
    }

    /**
     * Parses {@code --data}, {@code --bind host:port}, {@code --stdio}, {@code --repo URL}.
     *
     * @param args command line
     * @return config
     */
    public static ServerConfig parse(final String[] args) {
        Path data = defaultDataDir();
        String host = "127.0.0.1";
        int port = 8741;
        boolean stdio = false;
        final List<String> repos = new ArrayList<>(CatalogFetcher.envRepos());
        if (Objects.nonNull(args)) {
            for (int i = 0; i < args.length; i++) {
                final String arg = args[i];
                if ("--data".equals(arg) && i + 1 < args.length) {
                    i++;
                    data = Path.of(args[i]);
                } else if ("--bind".equals(arg) && i + 1 < args.length) {
                    i++;
                    final String[] parts = args[i].split(":", 2);
                    host = parts[0];
                    port = Integer.parseInt(parts[1]);
                } else if ("--stdio".equals(arg)) {
                    stdio = true;
                } else if ("--repo".equals(arg) && i + 1 < args.length) {
                    i++;
                    repos.add(args[i]);
                }
            }
        }
        return new ServerConfig(data, new InetSocketAddress(host, port), stdio, repos);
    }

    /**
     * @return {@code AXIOM_DATA} or {@code ~/.axiom}
     */
    public static Path defaultDataDir() {
        final String env = System.getenv("AXIOM_DATA");
        if (Objects.nonNull(env) && !env.isBlank()) {
            return Path.of(env);
        }
        return Path.of(System.getProperty("user.home"), ".axiom");
    }
}
