package org.dempsay.axiom.mcp.ingest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import org.dempsay.utils.exceptional.api.ExceptionalResponse;
import org.dempsay.utils.exceptional.api.ExceptionalSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Reads remote repositories and matching {@code <server>} credentials from Maven
 * {@code settings.xml}.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public final class MavenSettings {

    private static final Logger LOG = LoggerFactory.getLogger(MavenSettings.class);

    private MavenSettings() { }

    /**
     * @return {@code ~/.m2/settings.xml}
     */
    public static Path defaultSettingsFile() {
        return Path.of(System.getProperty("user.home"), ".m2", "settings.xml");
    }

    /**
     * Repositories from active profiles. Missing or unreadable files yield an empty list.
     *
     * @param settingsXml Maven user settings
     * @return remotes with credentials when a {@code <server>} id matches
     */
    public static List<RemoteRepositorySpec> repositories(final Path settingsXml) {
        Objects.requireNonNull(settingsXml, "settingsXml");
        if (!Files.isRegularFile(settingsXml)) {
            return List.of();
        }
        final ExceptionalResponse<List<RemoteRepositorySpec>> parsed = parse(settingsXml);
        if (parsed.wasError()) {
            LOG.warn("Ignoring unreadable Maven settings {}", settingsXml);
            return List.of();
        }
        return parsed.response();
    }

    private static ExceptionalResponse<List<RemoteRepositorySpec>> parse(final Path settingsXml) {
        return ExceptionalSupplier.of(() -> {
            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);
            final Document document = factory.newDocumentBuilder().parse(settingsXml.toFile());
            final Element root = document.getDocumentElement();
            final Map<String, String[]> servers = servers(root);
            final Set<String> active = activeProfiles(root);
            final List<RemoteRepositorySpec> remotes = new ArrayList<>();
            final NodeList profiles = root.getElementsByTagName("profile");
            for (int i = 0; i < profiles.getLength(); i++) {
                final Element profile = (Element) profiles.item(i);
                final String profileId = childText(profile, "id");
                if (!active.contains(profileId) && !activeByDefault(profile)) {
                    continue;
                }
                addRepositories(profile, servers, remotes);
            }
            return List.copyOf(remotes);
        }).execute();
    }

    private static Map<String, String[]> servers(final Element root) {
        final Map<String, String[]> servers = new HashMap<>();
        final NodeList nodes = root.getElementsByTagName("server");
        for (int i = 0; i < nodes.getLength(); i++) {
            final Element server = (Element) nodes.item(i);
            final String id = childText(server, "id");
            if (Objects.nonNull(id)) {
                servers.put(id, new String[] {childText(server, "username"), childText(server, "password")});
            }
        }
        return servers;
    }

    private static Set<String> activeProfiles(final Element root) {
        final Set<String> active = new HashSet<>();
        final NodeList nodes = root.getElementsByTagName("activeProfile");
        for (int i = 0; i < nodes.getLength(); i++) {
            final String id = nodes.item(i).getTextContent();
            if (Objects.nonNull(id) && !id.isBlank()) {
                active.add(id.trim());
            }
        }
        return active;
    }

    private static boolean activeByDefault(final Element profile) {
        final NodeList activations = profile.getElementsByTagName("activeByDefault");
        for (int i = 0; i < activations.getLength(); i++) {
            if ("true".equalsIgnoreCase(activations.item(i).getTextContent().trim())) {
                return true;
            }
        }
        return false;
    }

    private static void addRepositories(
            final Element profile,
            final Map<String, String[]> servers,
            final List<RemoteRepositorySpec> remotes) {
        final NodeList repos = profile.getElementsByTagName("repository");
        for (int i = 0; i < repos.getLength(); i++) {
            final Element repo = (Element) repos.item(i);
            final String id = childText(repo, "id");
            final String url = childText(repo, "url");
            if (Objects.isNull(id) || Objects.isNull(url)) {
                continue;
            }
            final String[] auth = servers.get(id);
            final String username = Objects.nonNull(auth) ? auth[0] : null;
            final String password = Objects.nonNull(auth) ? auth[1] : null;
            remotes.add(new RemoteRepositorySpec(id, url, username, password));
        }
    }

    private static String childText(final Element parent, final String name) {
        final NodeList nodes = parent.getElementsByTagName(name);
        if (nodes.getLength() == 0) {
            return null;
        }
        final String text = nodes.item(0).getTextContent();
        if (Objects.isNull(text) || text.isBlank()) {
            return null;
        }
        return text.trim();
    }
}
