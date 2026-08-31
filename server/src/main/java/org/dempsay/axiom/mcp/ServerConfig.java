package org.dempsay.axiom.mcp;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Process flags for {@link AxiomServer}.
 *
 * @param dataDir catalog store
 * @param bind listen address
 * @param stdio if true, also run MCP on stdin/stdout
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public record ServerConfig(Path dataDir, InetSocketAddress bind, boolean stdio) {

    /**
     * Parses {@code --data}, {@code --bind host:port}, {@code --stdio}.
     *
     * @param args command line
     * @return config
     */
    public static ServerConfig parse(final String[] args) {
        Path data = defaultDataDir();
        String host = "127.0.0.1";
        int port = 8741;
        boolean stdio = false;
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
                }
            }
        }
        return new ServerConfig(data, new InetSocketAddress(host, port), stdio);
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
