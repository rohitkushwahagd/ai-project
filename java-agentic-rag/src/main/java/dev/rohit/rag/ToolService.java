package dev.rohit.rag;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;

@Service
public class ToolService {
    private final SourceRepository repository;
    public ToolService(SourceRepository repository) { this.repository=repository; }
    public Object call(String name,Map<String,Object> args,TenantAccess access) {
        UUID id=UUID.fromString(Objects.toString(args.get("sourceId"),""));
        var source=repository.get(access.tenant(),id);
        return switch(name) {
            case "ticket_lookup" -> {
                if(!source.kind().equals("tickets")) throw new IllegalArgumentException("Not a ticket source");
                yield excerpt(source);
            }
            case "runbook_fetch" -> {
                if(!source.kind().equals("runbooks")) throw new IllegalArgumentException("Not a runbook source");
                yield excerpt(source);
            }
            case "escalation" -> {
                if(!access.escalate()) throw new AccessDeniedException("rag.escalate scope required");
                if(!source.kind().equals("tickets")) throw new IllegalArgumentException("Only tickets can be escalated");
                String reason=Objects.toString(args.get("reason"),"").trim();
                if(reason.isEmpty() || reason.length()>1000) throw new IllegalArgumentException("Reason must contain 1–1000 characters");
                yield Map.of("id",repository.escalate(access.tenant(),id,reason),"status","RECORDED_SYNTHETIC");
            }
            default -> throw new IllegalArgumentException("Unknown tool");
        };
    }
    private Map<String,Object> excerpt(ApiTypes.Source source) {
        int end=source.content().offsetByCodePoints(0,Math.min(2000,source.content().codePointCount(0,source.content().length())));
        return Map.of("sourceId",source.id(),"title",source.title(),"kind",source.kind(),
            "text",source.content().substring(0,end),"truncated",end<source.content().length());
    }
}
