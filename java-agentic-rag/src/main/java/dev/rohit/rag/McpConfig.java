package dev.rohit.rag;

import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.*;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.servlet.function.*;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.*;
import io.modelcontextprotocol.server.transport.WebMvcStatelessServerTransport;
import io.modelcontextprotocol.spec.McpSchema;

@Configuration
public class McpConfig {
    @Bean WebMvcStatelessServerTransport mcpTransport(ObjectMapper json,JwtDecoder decoder) {
        return WebMvcStatelessServerTransport.builder().jsonMapper(new JacksonMcpJsonMapper(json))
            .messageEndpoint("/mcp").contextExtractor(request->{
                String header=request.headers().firstHeader("Authorization");
                if(header==null || !header.startsWith("Bearer ")) throw new AccessDeniedException("Bearer token required");
                // Copy validated identity into transport context; never trust a tool argument for tenancy.
                return McpTransportContext.create(Map.of("access",TenantAccess.from(decoder.decode(header.substring(7)))));
            }).build();
    }
    @Bean RouterFunction<ServerResponse> mcpRoutes(WebMvcStatelessServerTransport transport) {
        return transport.getRouterFunction();
    }
    public static String schema(boolean action) {
        return "{\"type\":\"object\",\"properties\":{\"sourceId\":{\"type\":\"string\",\"format\":\"uuid\"}"
            +(action?",\"reason\":{\"type\":\"string\",\"minLength\":1,\"maxLength\":1000}":"")
            +"},\"required\":[\"sourceId\""+(action?",\"reason\"":"")+"],\"additionalProperties\":false}";
    }
    @Bean(destroyMethod="close") McpStatelessSyncServer mcpServer(WebMvcStatelessServerTransport transport,ToolService tools,ObjectMapper json) {
        var mapper=new JacksonMcpJsonMapper(json);
        var specifications=new ArrayList<McpStatelessServerFeatures.SyncToolSpecification>();
        for(String name:List.of("ticket_lookup","runbook_fetch","escalation")) {
            var tool=McpSchema.Tool.builder().name(name)
                .description(name.equals("escalation")?"Record an authorized synthetic ticket escalation. Requires rag.escalate scope.":"Read a tenant-authorized "+name+" source by sourceId.")
                .inputSchema(mapper,schema(name.equals("escalation"))).build();
            specifications.add(new McpStatelessServerFeatures.SyncToolSpecification(tool,(context,request)->{
                try {
                    var access=(TenantAccess)context.get("access");
                    if(access==null) throw new AccessDeniedException("Missing identity");
                    return new McpSchema.CallToolResult(json.writeValueAsString(tools.call(name,request.arguments(),access)),false);
                } catch(Exception ex) { return new McpSchema.CallToolResult("Tool request denied or invalid; verify scope and source ID.",true); }
            }));
        }
        return McpServer.sync(transport).jsonMapper(mapper).serverInfo("project1-tools","1.0.0")
            .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build()).tools(specifications).build();
    }
}
