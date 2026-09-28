package org.dempsay.axiom.mcp;

import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.Tool;

import org.dempsay.axiom.mcp.ingest.CatalogLookup;
import org.dempsay.axiom.mcp.ingest.IndexStore;
import org.dempsay.utils.exceptional.api.ExceptionalResponse;
import org.dempsay.utils.exceptional.api.ExceptionalSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Optional stdio MCP transport in the same process as HTTP.
 *
 * @author Shawn Dempsay {@literal <shawn@dempsay.org>}
 * @since 1.0.0
 */
public final class McpStdio {

    private static final Logger LOG = LoggerFactory.getLogger(McpStdio.class);

    private static final ObjectMapper JSON = new ObjectMapper();

    private McpStdio() { }

    /**
     * Serves MCP on stdin/stdout until the client disconnects. Blocks.
     *
     * @param store live index
     */
    public static void serve(final IndexStore store) {
        LOG.info("axiom-mcp stdio MCP starting");
        final McpJsonMapper json = new JacksonMcpJsonMapper(new ObjectMapper());
        final StdioServerTransportProvider transport = new StdioServerTransportProvider(json);
        final Tool lookup = tool(json, "catalog_lookup", "Look up a blessed implementation",
                CatalogLookup.LOOKUP_DESCRIPTION, """
                        {
                          "type": "object",
                          "properties": {
                            "query": { "type": "string", "description": "Job the caller is trying to do" },
                            "language": { "type": "string" },
                            "limit": { "type": "integer", "default": 3 }
                          },
                          "required": ["query"]
                        }
                        """);
        final Tool search = tool(json, "catalog_search", "Search catalog intents",
                CatalogLookup.SEARCH_DESCRIPTION, """
                        {
                          "type": "object",
                          "properties": {
                            "query": { "type": "string" },
                            "limit": { "type": "integer", "default": 10 },
                            "includeSnippet": { "type": "boolean", "default": false }
                          },
                          "required": ["query"]
                        }
                        """);
        final Tool get = tool(json, "catalog_get", "Get an intent by id or alias",
                CatalogLookup.GET_DESCRIPTION, """
                        {
                          "type": "object",
                          "properties": {
                            "id": { "type": "string", "description": "Intent id or alias" }
                          },
                          "required": ["id"]
                        }
                        """);
        final McpSyncServer server = McpServer.sync(transport)
                .serverInfo("axiom-mcp", "1.1.0-SNAPSHOT")
                .capabilities(ServerCapabilities.builder().tools(true).build())
                .toolCall(lookup, (exchange, request) -> jsonResult(CatalogLookup.lookup(
                        store,
                        textArg(request, "query"),
                        textArg(request, "language"),
                        intArg(request, "limit", 0)), false))
                .toolCall(search, (exchange, request) -> jsonResult(CatalogLookup.search(
                        store,
                        textArg(request, "query"),
                        intArg(request, "limit", 0),
                        boolArg(request, "includeSnippet")), false))
                .toolCall(get, (exchange, request) -> {
                    final CatalogLookup.Result result = CatalogLookup.get(store, textArg(request, "id"));
                    return jsonResult(result, !result.ok());
                })
                .build();
        LOG.info("axiom-mcp stdio MCP ready {}", server);
    }

    private static Tool tool(
            final McpJsonMapper json,
            final String name,
            final String title,
            final String description,
            final String schema) {
        return Tool.builder()
                .name(name)
                .title(title)
                .description(description)
                .inputSchema(json, schema)
                .build();
    }

    private static CallToolResult jsonResult(final Object payload, final boolean error) {
        final ExceptionalResponse<String> text = ExceptionalSupplier.of(() -> JSON.writeValueAsString(payload))
                .execute();
        final String body = text.wasError() ? "{\"ok\":false,\"error\":\"failed to encode result\"}" : text.response();
        return CallToolResult.builder()
                .addTextContent(body)
                .isError(error)
                .build();
    }

    private static String textArg(final CallToolRequest request, final String name) {
        final Object value = arg(request, name);
        return Objects.isNull(value) ? null : String.valueOf(value);
    }

    private static int intArg(final CallToolRequest request, final String name, final int fallback) {
        final Object value = arg(request, name);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (Objects.isNull(value)) {
            return fallback;
        }
        final ExceptionalResponse<Integer> parsed =
                ExceptionalSupplier.of(() -> Integer.parseInt(String.valueOf(value))).execute();
        return parsed.wasError() ? fallback : parsed.response();
    }

    private static boolean boolArg(final CallToolRequest request, final String name) {
        final Object value = arg(request, name);
        if (value instanceof Boolean flag) {
            return flag;
        }
        return "true".equalsIgnoreCase(String.valueOf(value));
    }

    private static Object arg(final CallToolRequest request, final String name) {
        final Map<String, Object> arguments = request.arguments();
        if (Objects.isNull(arguments)) {
            return null;
        }
        return arguments.get(name);
    }
}
