package dev.rohit.rag;

import java.util.*;
import java.util.concurrent.*;
import java.io.IOException;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import static dev.rohit.rag.ApiTypes.*;

@RestController
public class ProjectController {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(ProjectController.class);
    private final SourceRepository sources;
    private final QueryService queries;
    private final Semaphore capacity=new Semaphore(4);
    private final ExecutorService executor=Executors.newVirtualThreadPerTaskExecutor();
    public ProjectController(SourceRepository sources,QueryService queries) { this.sources=sources; this.queries=queries; }
    @jakarta.annotation.PreDestroy void close() { executor.shutdownNow(); }
    @PostMapping({"/ingest","/api/documents"})
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String,Object> ingest(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Ingest input,
                                    @RequestHeader(value="Idempotency-Key",required=false) String key) {
        var access=TenantAccess.from(jwt); UUID id=sources.ingest(access.tenant(),input,key);
        return Map.of("id",id,"status",sources.get(access.tenant(),id).status());
    }
    @GetMapping("/sources/{id}")
    public Source source(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id) { return sources.get(TenantAccess.from(jwt).tenant(),id); }
    @GetMapping("/api/documents")
    public List<DocumentSummary> documents(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="0") int page) {
        return sources.list(TenantAccess.from(jwt).tenant(),page);
    }
    @PutMapping("/sources/{id}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String,Object> replace(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,
                                     @Valid @RequestBody Ingest input,@RequestHeader("Idempotency-Key") String key) {
        UUID version=sources.replace(TenantAccess.from(jwt).tenant(),id,input,key);
        return Map.of("id",version,"status",sources.get(TenantAccess.from(jwt).tenant(),version).status());
    }
    @DeleteMapping("/sources/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id) { sources.delete(TenantAccess.from(jwt).tenant(),id); }
    @PostMapping("/feedback")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String,UUID> feedback(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Feedback input) {
        return Map.of("id",sources.feedback(TenantAccess.from(jwt).tenant(),input));
    }
    @PostMapping(value="/query",produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter query(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Question input) {
        var access=TenantAccess.from(jwt);
        if(!capacity.tryAcquire()) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Local model busy");
        var emitter=new SseEmitter(300_000L);
        var future=new java.util.concurrent.atomic.AtomicReference<Future<?>>();
        var released=new java.util.concurrent.atomic.AtomicBoolean();
        var terminal=new java.util.concurrent.atomic.AtomicBoolean();
        Runnable release=()->{ if(released.compareAndSet(false,true)) capacity.release(); };
        Runnable cancel=()->{ if(!terminal.get()) { var f=future.get(); if(f!=null) f.cancel(true); } };
        var requestId=UUID.randomUUID();
        FutureTask<Void> task=new FutureTask<>(()->{
            try {
                Result result=queries.answer(access,input,s->send(emitter,"sources",s),t->send(emitter,"token",t));
                send(emitter,"done",result); terminal.set(true); emitter.complete();
            } catch(Exception ex) {
                try { send(emitter,"error",Map.of("message","Query failed. Inspect service logs and dependency health.","requestId",requestId)); } catch(Exception ignored) {}
                log.warn("Query {} failed: {}",requestId,diagnostic(ex));
                terminal.set(true); emitter.complete();
            }
            return null;
        });
        future.set(task);
        emitter.onTimeout(cancel); emitter.onError(e->cancel.run()); emitter.onCompletion(cancel);
        // Cancellation marks a Future done immediately, even if a dependency has not
        // stopped yet. Keep its permit until the worker actually exits.
        try { executor.execute(()->{ try { task.run(); } finally { release.run(); } }); }
        catch(RejectedExecutionException ex) { task.cancel(false); release.run(); throw new IllegalStateException("Query executor is shutting down",ex); }
        return emitter;
    }
    private static String diagnostic(Throwable error) {
        var parts=new ArrayList<String>();
        for(Throwable current=error; current!=null && parts.size()<6; current=current.getCause()) {
            String message=current.getMessage()==null?"":current.getMessage().replaceAll("[\\r\\n\\t]"," ");
            message=message.replaceAll("(?i)Bearer\\s+\\S+","Bearer [redacted]")
                .replaceAll("(?i)(api[-_ ]?key|token|secret|password|authorization)(\\s*[=:]\\s*)\\S+","$1$2[redacted]");
            if(message.length()>240) message=message.substring(0,240)+"…";
            parts.add(current.getClass().getSimpleName()+(message.isBlank()?"":": "+message));
        }
        return String.join(" <- ",parts);
    }
    private static void send(SseEmitter emitter,String name,Object data) {
        try { emitter.send(SseEmitter.event().name(name).data(data)); }
        catch(IOException ex) { throw new IllegalStateException("Client disconnected",ex); }
    }
}
