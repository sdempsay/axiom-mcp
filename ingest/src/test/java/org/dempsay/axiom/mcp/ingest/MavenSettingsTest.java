package org.dempsay.axiom.mcp.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Maven {@code settings.xml} remotes and matching server credentials.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
class MavenSettingsTest {

    @TempDir
    private Path temp;

    @Test
    void readsActiveProfileRepositoryAndServer() throws Exception {
        final Path settings = temp.resolve("settings.xml");
        Files.writeString(settings, """
                <settings>
                  <servers>
                    <server>
                      <id>private</id>
                      <username>user</username>
                      <password>secret</password>
                    </server>
                  </servers>
                  <profiles>
                    <profile>
                      <id>private</id>
                      <repositories>
                        <repository>
                          <id>private</id>
                          <url>https://example.invalid/maven/</url>
                        </repository>
                      </repositories>
                    </profile>
                  </profiles>
                  <activeProfiles>
                    <activeProfile>private</activeProfile>
                  </activeProfiles>
                </settings>
                """);
        final List<RemoteRepositorySpec> remotes = MavenSettings.repositories(settings);
        assertEquals(1, remotes.size());
        assertEquals("private", remotes.get(0).id());
        assertEquals("https://example.invalid/maven/", remotes.get(0).url());
        assertEquals("user", remotes.get(0).username());
        assertEquals("secret", remotes.get(0).password());
    }

    @Test
    void standardMergesExtraSettingsAndCentral() throws Exception {
        final Path settings = temp.resolve("settings.xml");
        Files.writeString(settings, """
                <settings>
                  <profiles>
                    <profile>
                      <id>github</id>
                      <activation><activeByDefault>true</activeByDefault></activation>
                      <repositories>
                        <repository>
                          <id>github</id>
                          <url>https://maven.pkg.github.com/example/lib</url>
                        </repository>
                      </repositories>
                    </profile>
                  </profiles>
                </settings>
                """);
        final CatalogFetcher fetcher = CatalogFetcher.standard(
                temp.resolve("m2"),
                settings,
                List.of("https://custom.example/repo"));
        final List<String> urls = fetcher.remoteSpecs().stream().map(RemoteRepositorySpec::url).toList();
        assertTrue(urls.contains("https://custom.example/repo"));
        assertTrue(urls.contains("https://maven.pkg.github.com/example/lib"));
        assertTrue(urls.contains(CatalogFetcher.CENTRAL));
    }
}
