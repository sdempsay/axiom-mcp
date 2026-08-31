package org.dempsay.axiom.mcp.ingest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.dempsay.axiom.model.Catalog;
import org.dempsay.axiom.model.CatalogValidator;
import org.dempsay.axiom.model.ValidationResult;
import org.dempsay.utils.exceptional.api.ExceptionalResource;
import org.dempsay.utils.exceptional.api.ExceptionalResponse;
import org.dempsay.utils.exceptional.api.ExceptionalSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adds a catalog by Maven GAV: fetch, validate, merge, then store. A merge conflict
 * leaves the data dir unchanged.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public final class CatalogIngest {

    private static final Logger LOG = LoggerFactory.getLogger(CatalogIngest.class);

    private CatalogIngest() { }

    /**
     * Result of an add.
     *
     * @param failed true on missing classifier, invalid catalog, or merge conflict
     * @param message error text when failed
     * @param index live index when successful
     */
    public record Outcome(boolean failed, String message, CatalogIndex index) {
    }

    /**
     * Fetches {@code gav}, replaces that version on disk when the merge succeeds.
     *
     * @param store open store
     * @param gav coordinates
     * @param fetcher Maven Resolver fetch
     * @return outcome or I/O failure
     */
    public static ExceptionalResponse<Outcome> add(
            final IndexStore store,
            final Gav gav,
            final CatalogFetcher fetcher) {
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(gav, "gav");
        Objects.requireNonNull(fetcher, "fetcher");
        return fetcher.fetch(gav).chain((listener, fetched) -> {
            if (fetched.missing()) {
                return ExceptionalResponse.success(new Outcome(true, fetched.message(), null));
            }
            return stage(store, gav, fetched);
        });
    }

    private static ExceptionalResponse<Outcome> stage(
            final IndexStore store,
            final Gav gav,
            final CatalogFetcher.FetchResult fetched) {
        return ExceptionalSupplier.of(() -> Files.createTempDirectory("axiom-ingest-"))
                .execute()
                .chain((listener, stage) -> addFromStage(store, gav, fetched, stage));
    }

    private static ExceptionalResponse<Outcome> addFromStage(
            final IndexStore store,
            final Gav gav,
            final CatalogFetcher.FetchResult fetched,
            final Path stage) {
        return ExceptionalSupplier.of(() -> {
            try {
                return installStaged(store, gav, fetched, stage);
            } finally {
                deleteRecursively(stage);
            }
        }).execute();
    }

    private static Outcome installStaged(
            final IndexStore store,
            final Gav gav,
            final CatalogFetcher.FetchResult fetched,
            final Path stage) throws IOException {
        final Path stagedYaml = stage.resolve(gav.version() + ".yaml");
        Files.copy(fetched.catalogYaml(), stagedYaml, StandardCopyOption.REPLACE_EXISTING);
        if (Objects.nonNull(fetched.examplesZip())) {
            unzip(fetched.examplesZip(), stage);
        }
        final ExceptionalResponse<ValidationResult> validated = CatalogValidator.validate(stagedYaml);
        if (validated.wasError()) {
            throw new IOException("Failed to read catalog for " + gav.compact());
        }
        final ValidationResult result = validated.response();
        if (!result.isValid()) {
            return new Outcome(true, "Invalid catalog for " + gav.compact(), null);
        }
        final Path dest = store.catalogYaml(gav);
        final ExceptionalResponse<List<Catalog>> loaded = store.loadCatalogsExcept(dest);
        if (loaded.wasError()) {
            throw new IOException("Failed to load catalogs from " + store.catalogsDir());
        }
        final List<Catalog> mergedInput = new ArrayList<>(loaded.response());
        mergedInput.add(result.catalog());
        final Merger.Outcome merged = Merger.merge(mergedInput);
        if (merged.failed()) {
            LOG.error("Merge conflict adding {}: {}", gav.compact(), merged.message());
            return new Outcome(true, merged.message(), null);
        }
        Files.createDirectories(dest.getParent());
        Files.copy(stagedYaml, dest, StandardCopyOption.REPLACE_EXISTING);
        copySnippets(stage, stagedYaml, dest.getParent());
        final ExceptionalResponse<CatalogIndex> reloaded = store.reload();
        if (reloaded.wasError()) {
            throw new IOException("Failed to reload index after adding " + gav.compact());
        }
        return new Outcome(false, "Added " + gav.compact(), reloaded.response());
    }

    private static void unzip(final Path zip, final Path dest) throws IOException {
        final ExceptionalResponse<Path> unpacked = ExceptionalResource.of(
                () -> new ZipInputStream(Files.newInputStream(zip)),
                in -> unpack(in, dest)).execute();
        if (unpacked.wasError()) {
            throw new IOException("Failed to unpack examples zip " + zip);
        }
    }

    private static Path unpack(final ZipInputStream in, final Path dest) throws IOException {
        ZipEntry entry = in.getNextEntry();
        while (Objects.nonNull(entry)) {
            if (!entry.isDirectory()) {
                final Path out = dest.resolve(entry.getName()).normalize();
                if (!out.startsWith(dest)) {
                    throw new IOException("Zip entry escapes destination: " + entry.getName());
                }
                Files.createDirectories(out.getParent());
                Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
            }
            in.closeEntry();
            entry = in.getNextEntry();
        }
        return dest;
    }

    private static void copySnippets(final Path stage, final Path stagedYaml, final Path destDir) throws IOException {
        try (Stream<Path> stream = Files.walk(stage)) {
            final List<Path> files = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> !path.equals(stagedYaml))
                    .toList();
            for (final Path file : files) {
                final Path target = destDir.resolve(stage.relativize(file));
                Files.createDirectories(target.getParent());
                Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static void deleteRecursively(final Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            final List<Path> paths = stream.sorted(Comparator.reverseOrder()).toList();
            for (final Path path : paths) {
                Files.deleteIfExists(path);
            }
        }
    }
}
