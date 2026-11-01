package dev.rohit.rag;

import java.time.Duration;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class AgentTools {
    private final String url;
    private final ObjectMapper json;
    public AgentTools(@Value("${app.mcp.url:http://localhost:8080}") String url,ObjectMapper json) { this.url=url; this.json=json; }
    public Session open(TenantAccess access) {
        var transport=HttpClientStreamableHttpTransport.builder(url).endpoint("/mcp")
            .openConnectionOnStartup(false).customizeRequest(r->r.header("Authorization","Bearer "+access.token())).build();
        var client=McpClient.sync(transport).requestTimeout(Duration.ofSeconds(30)).build();
        try { client.initialize(); return new Session(client,access); }
        catch(RuntimeException ex) { client.close(); throw ex; }
    }
    public final class Session implements AutoCloseable {
        private final McpSyncClient client;
        private final TenantAccess access;
        private int calls;
        private final List<ApiTypes.ToolTrace> trace=new ArrayList<>();
        private Session(McpSyncClient client,TenantAccess access) { this.client=client; this.access=access; }
        public ToolCallback[] callbacks() {
            return client.listTools().tools().stream().filter(t->access.escalate() || !t.name().equals("escalation"))
                .map(t->(ToolCallback)new ToolCallback() {
                    public ToolDefinition getToolDefinition() { return ToolDefinition.builder().name(t.name()).description(t.description())
                        .inputSchema(McpConfig.schema(t.name().equals("escalation"))).build(); }
                    @SuppressWarnings("unchecked") public String call(String input) {
                        if(++calls>5) throw new IllegalStateException("Tool call budget exceeded");
                        try {
                            var result=client.callTool(new McpSchema.CallToolRequest(t.name(),json.readValue(input,Map.class)));
                            String encoded=json.writeValueAsString(result);
                            trace.add(new ApiTypes.ToolTrace(t.name(),Boolean.TRUE.equals(result.isError()),encoded));
                            return encoded;
                        }
                        catch(Exception ex) { throw new IllegalStateException("MCP tool call failed",ex); }
                    }
                }).toArray(ToolCallback[]::new);
        }
        public List<ApiTypes.ToolTrace> trace() { return List.copyOf(trace); }
        public void close() { client.close(); }
    }
}
