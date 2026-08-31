package org.dempsay.axiom.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.Tool;

import org.dempsay.axiom.mcp.ingest.IndexStore;
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
        final String schema = """
                {
                  "type": "object",
                  "properties": {
                    "query": { "type": "string", "description": "Job the caller is trying to do" }
                  },
                  "required": ["query"]
                }
                """;
        final Tool lookup = Tool.builder()
                .name("catalog_lookup")
                .title("Look up a blessed implementation")
                .description("Call catalog_lookup before writing a helper, try/catch for I/O, or a new *Util class.")
                .inputSchema(json, schema)
                .build();
        final McpSyncServer server = McpServer.sync(transport)
                .serverInfo("axiom-mcp", "0.1.0-SNAPSHOT")
                .capabilities(ServerCapabilities.builder().tools(true).build())
                .toolCall(lookup, (exchange, request) -> {
                    final int intents = store.index().intents().size();
                    final String text = "{\"hits\":[],\"intents\":" + intents
                            + ",\"hint\":\"empty result; try catalog_search or catalog_gap\"}";
                    return CallToolResult.builder()
                            .addTextContent(text)
                            .isError(false)
                            .build();
                })
                .build();
        LOG.info("axiom-mcp stdio MCP ready {}", server);
    }
}
