package org.dempsay.axiom.mcp.ingest;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import org.dempsay.utils.exceptional.api.ExceptionalResponse;
import org.dempsay.utils.exceptional.api.ExceptionalSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession.CloseableSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.supplier.RepositorySystemSupplier;
import org.eclipse.aether.supplier.SessionBuilderSupplier;

/**
 * Fetches {@code classifier=agent-catalog} (and optional examples zip) with Maven Resolver.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public final class CatalogFetcher {

    private static final Logger LOG = LoggerFactory.getLogger(CatalogFetcher.class);

    private static final String CATALOG_CLASSIFIER = "agent-catalog";

    private static final String EXAMPLES_CLASSIFIER = "agent-catalog-examples";

    private static final String CENTRAL = "https://repo.maven.apache.org/maven2/";

    private final Path localRepository;

    private final List<String> remoteUrls;

    /**
     * @param localRepository Maven local repository
     * @param remoteUrls remote repository base URLs
     */
    public CatalogFetcher(final Path localRepository, final List<String> remoteUrls) {
        this.localRepository = Objects.requireNonNull(localRepository, "localRepository");
        this.remoteUrls = List.copyOf(Objects.requireNonNull(remoteUrls, "remoteUrls"));
    }

    /**
     * @return local {@code ~/.m2/repository} plus Maven Central
     */
    public static CatalogFetcher defaults() {
        return new CatalogFetcher(
                Path.of(System.getProperty("user.home"), ".m2", "repository"),
                List.of(CENTRAL));
    }

    /**
     * Result of resolving one GAV.
     *
     * @param missing true when {@code classifier=agent-catalog} was not found
     * @param message error text when missing
     * @param catalogYaml resolved catalog yaml, or null when missing
     * @param examplesZip optional examples zip
     */
    public record FetchResult(boolean missing, String message, Path catalogYaml, Path examplesZip) {
    }

    /**
     * Resolves {@code g:a:v:yaml:agent-catalog}. Missing classifier is a result, not a silent skip.
     * The examples zip is optional.
     *
     * @param gav coordinates
     * @return fetch result or I/O / resolver failure
     */
    public ExceptionalResponse<FetchResult> fetch(final Gav gav) {
        Objects.requireNonNull(gav, "gav");
        return ExceptionalSupplier.of(() -> {
            try (RepositorySystem system = new RepositorySystemSupplier().get();
                    CloseableSession session = new SessionBuilderSupplier(system)
                            .get()
                            .withLocalRepositoryBaseDirectories(localRepository)
                            .setUpdatePolicy(RepositoryPolicy.UPDATE_POLICY_ALWAYS)
                            .setChecksumPolicy(RepositoryPolicy.CHECKSUM_POLICY_WARN)
                            .build()) {
                return resolve(system, session, gav);
            }
        }).execute();
    }

    private FetchResult resolve(
            final RepositorySystem system,
            final CloseableSession session,
            final Gav gav) {
        final List<RemoteRepository> remotes = remotes();
        try {
            final Path yaml = resolveArtifact(system, session, remotes, gav, CATALOG_CLASSIFIER, "yaml");
            Path zip = null;
            try {
                zip = resolveArtifact(system, session, remotes, gav, EXAMPLES_CLASSIFIER, "zip");
            } catch (ArtifactResolutionException ex) {
                LOG.debug("No {} zip for {}: {}", EXAMPLES_CLASSIFIER, gav.compact(), ex.toString());
                zip = null;
            }
            return new FetchResult(false, null, yaml, zip);
        } catch (ArtifactResolutionException ex) {
            return new FetchResult(
                    true,
                    "Missing classifier=agent-catalog for " + gav.compact(),
                    null,
                    null);
        }
    }

    private List<RemoteRepository> remotes() {
        final RepositoryPolicy always = new RepositoryPolicy(
                true,
                RepositoryPolicy.UPDATE_POLICY_ALWAYS,
                RepositoryPolicy.CHECKSUM_POLICY_WARN);
        return remoteUrls.stream()
                .map(url -> new RemoteRepository.Builder("remote-" + url.hashCode(), "default", url)
                        .setReleasePolicy(always)
                        .setSnapshotPolicy(always)
                        .build())
                .toList();
    }

    private static Path resolveArtifact(
            final RepositorySystem system,
            final CloseableSession session,
            final List<RemoteRepository> remotes,
            final Gav gav,
            final String classifier,
            final String extension) throws ArtifactResolutionException {
        final ArtifactRequest request = new ArtifactRequest();
        request.setArtifact(new DefaultArtifact(
                gav.groupId(),
                gav.artifactId(),
                classifier,
                extension,
                gav.version()));
        request.setRepositories(remotes);
        final ArtifactResult result = system.resolveArtifact(session, request);
        return result.getArtifact().getPath();
    }
}
