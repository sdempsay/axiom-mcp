package org.dempsay.axiom.mcp.ingest;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

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
import org.eclipse.aether.util.repository.AuthenticationBuilder;

import org.dempsay.utils.exceptional.api.ExceptionalResponse;
import org.dempsay.utils.exceptional.api.ExceptionalSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    static final String CENTRAL = "https://repo.maven.apache.org/maven2/";

    private final Path localRepository;

    private final List<RemoteRepositorySpec> remoteSpecs;

    /**
     * URL-only remotes (tests, file repositories). No {@code settings.xml} lookup.
     *
     * @param localRepository Maven local repository
     * @param remoteUrls remote repository base URLs
     */
    public CatalogFetcher(final Path localRepository, final List<String> remoteUrls) {
        this(localRepository, specsFromUrls(remoteUrls).toArray(RemoteRepositorySpec[]::new));
    }

    private CatalogFetcher(final Path localRepository, final RemoteRepositorySpec... remoteSpecs) {
        this.localRepository = Objects.requireNonNull(localRepository, "localRepository");
        this.remoteSpecs = List.of(Objects.requireNonNull(remoteSpecs, "remoteSpecs"));
    }

    /**
     * Local {@code ~/.m2/repository}, extra URLs, {@code settings.xml} remotes, then Maven Central.
     *
     * @param extraRemoteUrls {@code --repo} / {@code AXIOM_REPOS} URLs
     * @return fetcher
     */
    public static CatalogFetcher standard(final List<String> extraRemoteUrls) {
        final Path m2 = Path.of(System.getProperty("user.home"), ".m2");
        return standard(m2.resolve("repository"), MavenSettings.defaultSettingsFile(), extraRemoteUrls);
    }

    /**
     * @param localRepository Maven local repository
     * @param settingsXml Maven user settings, may be missing
     * @param extraRemoteUrls extra remote URLs
     * @return fetcher
     */
    public static CatalogFetcher standard(
            final Path localRepository,
            final Path settingsXml,
            final List<String> extraRemoteUrls) {
        final LinkedHashMap<String, RemoteRepositorySpec> byUrl = new LinkedHashMap<>();
        int extraIndex = 0;
        final List<String> extras = Objects.nonNull(extraRemoteUrls) ? extraRemoteUrls : List.of();
        for (final String url : extras) {
            if (Objects.nonNull(url) && !url.isBlank()) {
                byUrl.putIfAbsent(normalizeUrl(url), new RemoteRepositorySpec("extra-" + extraIndex++, url, null, null));
            }
        }
        for (final RemoteRepositorySpec spec : MavenSettings.repositories(settingsXml)) {
            byUrl.putIfAbsent(normalizeUrl(spec.url()), spec);
        }
        byUrl.putIfAbsent(normalizeUrl(CENTRAL), new RemoteRepositorySpec("central", CENTRAL, null, null));
        return new CatalogFetcher(localRepository, byUrl.values().toArray(RemoteRepositorySpec[]::new));
    }

    /**
     * @return extra URLs, {@code settings.xml}, and Central
     */
    public static CatalogFetcher defaults() {
        return standard(envRepos());
    }

    /**
     * @return {@code AXIOM_REPOS} split on commas or whitespace
     */
    public static List<String> envRepos() {
        final String env = System.getenv("AXIOM_REPOS");
        if (Objects.isNull(env) || env.isBlank()) {
            return List.of();
        }
        return List.of(env.trim().split("[,\\s]+"));
    }

    /**
     * @return configured remotes (passwords omitted from {@link RemoteRepositorySpec#toString()})
     */
    public List<RemoteRepositorySpec> remoteSpecs() {
        return remoteSpecs;
    }

    private static List<RemoteRepositorySpec> specsFromUrls(final List<String> remoteUrls) {
        Objects.requireNonNull(remoteUrls, "remoteUrls");
        final List<RemoteRepositorySpec> specs = new ArrayList<>();
        int index = 0;
        for (final String url : remoteUrls) {
            specs.add(new RemoteRepositorySpec("remote-" + index++, url, null, null));
        }
        return specs;
    }

    private static String normalizeUrl(final String url) {
        if (url.endsWith("/")) {
            return url;
        }
        return url + "/";
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
        return remoteSpecs.stream()
                .map(spec -> remote(spec, always))
                .toList();
    }

    private static RemoteRepository remote(final RemoteRepositorySpec spec, final RepositoryPolicy always) {
        final RemoteRepository.Builder builder = new RemoteRepository.Builder(spec.id(), "default", spec.url())
                .setReleasePolicy(always)
                .setSnapshotPolicy(always);
        if (Objects.nonNull(spec.username()) && Objects.nonNull(spec.password())) {
            builder.setAuthentication(new AuthenticationBuilder()
                    .addUsername(spec.username())
                    .addPassword(spec.password())
                    .build());
        }
        return builder.build();
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
