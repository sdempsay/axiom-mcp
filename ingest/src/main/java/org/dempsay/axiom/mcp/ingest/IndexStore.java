package org.dempsay.axiom.mcp.ingest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import org.dempsay.axiom.model.Catalog;
import org.dempsay.axiom.model.CatalogValidator;
import org.dempsay.axiom.model.ValidationResult;
import org.dempsay.utils.exceptional.api.ExceptionalResource;
import org.dempsay.utils.exceptional.api.ExceptionalResponse;
import org.dempsay.utils.exceptional.api.ExceptionalSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Local-file catalog store. Data dir is {@code $AXIOM_DATA} or {@code ~/.axiom}.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public final class IndexStore {

    private static final Logger LOG = LoggerFactory.getLogger(IndexStore.class);

    private static final ObjectMapper YAML = YAMLMapper.builder()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
            .serializationInclusion(JsonInclude.Include.NON_EMPTY)
            .disable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();

    private final Path dataDir;
    private CatalogIndex index;
    private int catalogCount;

    private IndexStore(final Path dataDir, final CatalogIndex index, final int catalogCount) {
        this.dataDir = dataDir;
        this.index = index;
        this.catalogCount = catalogCount;
    }

    /**
     * Creates the data dir if missing, refuses unwritable dirs, loads catalogs, merges.
     *
     * @param dataDir store root
     * @return open store, or I/O / merge failure
     */
    public static ExceptionalResponse<IndexStore> open(final Path dataDir) {
        Objects.requireNonNull(dataDir, "dataDir");
        return ExceptionalSupplier.of(() -> {
            Files.createDirectories(dataDir);
            Files.createDirectories(dataDir.resolve("catalogs"));
            if (!Files.isWritable(dataDir)) {
                throw new IllegalStateException("Data dir is not writable: " + dataDir);
            }
            return dataDir;
        }).execute().chain((listener, dir) -> reloadInto(new IndexStore(dir, emptyIndex(), 0)));
    }

    /**
     * Reloads catalogs from disk and rewrites {@code index.yaml}.
     *
     * @return updated index
     */
    public ExceptionalResponse<CatalogIndex> reload() {
        return reloadInto(this).then(IndexStore::index);
    }

    /**
     * @return data dir
     */
    public Path dataDir() {
        return dataDir;
    }

    /**
     * @return last merged index
     */
    public CatalogIndex index() {
        return index;
    }

    /**
     * @return number of catalog yaml files loaded
     */
    public int catalogCount() {
        return catalogCount;
    }

    /**
     * @return catalogs directory
     */
    public Path catalogsDir() {
        return dataDir.resolve("catalogs");
    }

    private static ExceptionalResponse<IndexStore> reloadInto(final IndexStore store) {
        return loadCatalogs(store.catalogsDir()).chain((listener, catalogs) -> {
            final Merger.Outcome merged = Merger.merge(catalogs);
            if (merged.failed()) {
                LOG.error("Merge failed: {}", merged.message());
                return ExceptionalResponse.failure();
            }
            return writeIndex(store.dataDir.resolve("index.yaml"), merged.index()).then(written -> {
                store.index = written;
                store.catalogCount = catalogs.size();
                return store;
            });
        });
    }

    private static ExceptionalResponse<List<Catalog>> loadCatalogs(final Path catalogsDir) {
        return ExceptionalResource.of(() -> Files.walk(catalogsDir), stream -> readCatalogs(catalogsDir, stream))
                .execute();
    }

    private static List<Catalog> readCatalogs(final Path catalogsDir, final Stream<Path> stream) {
        final List<Path> files = stream
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".yaml"))
                .sorted()
                .toList();
        final List<Catalog> catalogs = new ArrayList<>();
        for (final Path file : files) {
            final ExceptionalResponse<ValidationResult> validated = CatalogValidator.validate(file);
            if (validated.wasError() || !validated.response().isValid()) {
                throw new IllegalStateException("Invalid catalog " + catalogsDir.relativize(file));
            }
            catalogs.add(validated.response().catalog());
        }
        return List.copyOf(catalogs);
    }

    private static ExceptionalResponse<CatalogIndex> writeIndex(final Path indexFile, final CatalogIndex index) {
        return ExceptionalSupplier.of(() -> {
            YAML.writeValue(indexFile.toFile(), index);
            return index;
        }).execute();
    }

    private static CatalogIndex emptyIndex() {
        return new CatalogIndex(1, "", List.of(), Map.of());
    }
}
