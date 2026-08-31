package org.dempsay.axiom.mcp.ingest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;

import org.dempsay.utils.exceptional.api.ExceptionalResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Maven Resolver fetch of {@code classifier=agent-catalog}.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
class CatalogFetcherTest {

    @TempDir
    private Path temp;

    @Test
    void missingClassifierFails() {
        final Path remote = temp.resolve("remote");
        final Path local = temp.resolve("local");
        final CatalogFetcher fetcher = TestMavenRepo.fetcher(remote, local);
        final Gav gav = new Gav("org.example", "missing", "1.0.0");
        final ExceptionalResponse<CatalogFetcher.FetchResult> fetched = fetcher.fetch(gav);
        assertFalse(fetched.wasError());
        assertTrue(fetched.response().missing());
        assertTrue(fetched.response().message().contains("agent-catalog"));
    }

    @Test
    void resolvesAgentCatalogClassifier() throws Exception {
        final Path remote = temp.resolve("remote");
        final Path local = temp.resolve("local");
        final Gav gav = new Gav("org.example", "lib", "1.0.0");
        TestMavenRepo.publish(
                remote,
                gav,
                TestMavenRepo.catalogYaml("org.example", "lib", "external_failure", "org.example.A.handle"),
                Map.of("s/snippet.java", "of().execute();"));
        final CatalogFetcher fetcher = TestMavenRepo.fetcher(remote, local);
        final ExceptionalResponse<CatalogFetcher.FetchResult> fetched = fetcher.fetch(gav);
        assertFalse(fetched.wasError(), "resolve failed");
        assertFalse(fetched.response().missing());
        assertTrue(fetched.response().catalogYaml().toString().endsWith(".yaml"));
        assertTrue(fetched.response().examplesZip().toString().endsWith(".zip"));
    }
}
