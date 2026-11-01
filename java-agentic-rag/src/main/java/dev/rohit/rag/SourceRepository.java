package dev.rohit.rag;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import static dev.rohit.rag.ApiTypes.*;

@Repository
public class SourceRepository {
    private final JdbcTemplate db;
    private final boolean exactRetrieval;
    public SourceRepository(JdbcTemplate db,@Value("${app.retrieval.exact:true}") boolean exactRetrieval) {
        this.db=db; this.exactRetrieval=exactRetrieval;
    }
    @Transactional
    public UUID enqueue(String tenant, Ingest input) {
        return ingest(tenant,input,null);
    }
    @Transactional
    public UUID ingest(String tenant, Ingest input, String requestKey) {
        UUID fingerprint=fingerprint("ingest",input);
        UUID replay=replay(tenant,requestKey,fingerprint);
        if(replay!=null) return replay;
        UUID id=TextChunker.id(tenant+"\u0000"+input.kind()+"\u0000"+input.title()+"\u0000"+input.text());
        db.update("INSERT INTO documents(id,tenant) VALUES(?,?) ON CONFLICT DO NOTHING",id,tenant);
        var document=lockDocument(tenant,id);
        if(Boolean.TRUE.equals(document.get("deleted"))) throw conflict("Document was deleted; use new content or a new title");
        int inserted=db.update("INSERT INTO sources(id,tenant,kind,title,content,document_id) VALUES(?,?,?,?,?,?) ON CONFLICT DO NOTHING",
                id,tenant,input.kind(),input.title(),input.text(),id);
        if(inserted==1) {
            db.update("UPDATE documents SET pending_source_id=? WHERE id=?",id,id);
            db.update("INSERT INTO ingestion_outbox(source_id,topic) VALUES(?,?)",id,"ingest."+input.kind());
        }
        else {
            // Lock the source so a concurrent consumer or repeat submission cannot race this retry.
            var status=db.queryForObject("SELECT status FROM sources WHERE id=? AND tenant=? FOR UPDATE",String.class,id,tenant);
            if("FAILED".equals(status) && id.equals(document.get("pending_source_id"))) {
                db.update("UPDATE sources SET status='QUEUED' WHERE id=? AND tenant=?",id,tenant);
                db.update("UPDATE ingestion_outbox SET published=false WHERE source_id=?",id);
            }
        }
        remember(tenant,requestKey,fingerprint,id);
        return id;
    }
    @Transactional
    public UUID replace(String tenant,UUID sourceId,Ingest input,String requestKey) {
        if(requestKey==null || requestKey.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Idempotency-Key required");
        UUID fingerprint=fingerprint("replace:"+sourceId,input);
        UUID replay=replay(tenant,requestKey,fingerprint);
        if(replay!=null) return replay;
        Source current=get(tenant,sourceId);
        UUID documentId=documentId(tenant,sourceId);
        var document=lockDocument(tenant,documentId);
        if(Boolean.TRUE.equals(document.get("deleted"))) throw notFound();
        if(!sourceId.equals(document.get("active_source_id"))) throw conflict("Replace the active source version");
        if(document.get("pending_source_id")!=null) throw conflict("A replacement is already pending");
        if(!current.kind().equals(input.kind())) throw conflict("A replacement cannot change the source kind");
        UUID version=UUID.randomUUID();
        db.update("INSERT INTO sources(id,tenant,kind,title,content,document_id) VALUES(?,?,?,?,?,?)",
                version,tenant,input.kind(),input.title(),input.text(),documentId);
        db.update("UPDATE documents SET pending_source_id=? WHERE id=?",version,documentId);
        db.update("INSERT INTO ingestion_outbox(source_id,topic) VALUES(?,?)",version,"ingest."+input.kind());
        remember(tenant,requestKey,fingerprint,version);
        return version;
    }
    @Transactional
    public void delete(String tenant,UUID sourceId) {
        UUID documentId=documentId(tenant,sourceId);
        var document=lockDocument(tenant,documentId);
        if(Boolean.TRUE.equals(document.get("deleted"))) return;
        db.update("UPDATE documents SET deleted=true,active_source_id=null,pending_source_id=null WHERE id=?",documentId);
        db.update("UPDATE sources SET status='DELETED' WHERE document_id=?",documentId);
        db.update("DELETE FROM chunks WHERE source_id IN (SELECT id FROM sources WHERE document_id=?)",documentId);
        bumpRevision(tenant);
    }
    private UUID documentId(String tenant,UUID sourceId) {
        return db.query("SELECT document_id FROM sources WHERE tenant=? AND id=?",(r,n)->r.getObject(1,UUID.class),tenant,sourceId)
                .stream().findFirst().orElseThrow(SourceRepository::notFound);
    }
    private Map<String,Object> lockDocument(String tenant,UUID id) {
        return db.queryForList("SELECT * FROM documents WHERE tenant=? AND id=? FOR UPDATE",tenant,id)
                .stream().findFirst().orElseThrow(SourceRepository::notFound);
    }
    private UUID replay(String tenant,String key,UUID fingerprint) {
        if(key==null) return null;
        if(key.isBlank() || key.length()>160) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid Idempotency-Key");
        // Serialize identical tenant/key admissions before inspecting or inserting their request record.
        db.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",tenant+":"+key);
        var rows=db.queryForList("SELECT * FROM ingestion_requests WHERE tenant=? AND request_key=?",tenant,key);
        if(rows.isEmpty()) return null;
        var row=rows.getFirst();
        if(!fingerprint.equals(row.get("payload_hash"))) throw conflict("Idempotency-Key was already used for a different request");
        return (UUID)row.get("source_id");
    }
    private void remember(String tenant,String key,UUID fingerprint,UUID id) {
        if(key!=null) db.update("INSERT INTO ingestion_requests(tenant,request_key,payload_hash,source_id) VALUES(?,?,?,?)",tenant,key,fingerprint,id);
    }
    private static UUID fingerprint(String operation,Ingest input) {
        // Length prefixes make field boundaries unambiguous even when content contains record-like delimiters.
        return TextChunker.id(operation+"|"+input.kind().length()+":"+input.kind()
                +input.title().length()+":"+input.title()+input.text().length()+":"+input.text());
    }
    private void bumpRevision(String tenant) {
        db.update("INSERT INTO tenant_revision(tenant,revision) VALUES(?,1) ON CONFLICT(tenant) DO UPDATE SET revision=tenant_revision.revision+1",tenant);
    }
    private static ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND,"Source not found"); }
    private static ResponseStatusException conflict(String reason) { return new ResponseStatusException(HttpStatus.CONFLICT,reason); }
    public Source get(String tenant,UUID id) {
        return db.query("SELECT s.* FROM sources s JOIN documents d ON d.id=s.document_id AND d.tenant=s.tenant WHERE s.tenant=? AND s.id=? AND NOT d.deleted",(r,n)->new Source(id,r.getString("kind"),
                r.getString("title"),r.getString("content"),r.getString("status")),tenant,id).stream().findFirst()
                .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Source not found"));
    }
    public long revision(String tenant) {
        return db.query("SELECT revision FROM tenant_revision WHERE tenant=?",(r,n)->r.getLong(1),tenant)
                .stream().findFirst().orElse(0L);
    }
    public List<DocumentSummary> list(String tenant,int page) {
        if(page<0 || page>10000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid page");
        return db.query("""
            SELECT s.id,s.kind,s.title,s.status,d.active_source_id,d.pending_source_id
            FROM documents d JOIN sources s ON s.id=COALESCE(d.pending_source_id,d.active_source_id,d.id) AND s.tenant=d.tenant
            WHERE d.tenant=? AND NOT d.deleted ORDER BY d.id LIMIT 20 OFFSET ?
            """,(r,n)->new DocumentSummary(r.getObject("id",UUID.class),r.getString("kind"),r.getString("title"),
                r.getString("status"),r.getObject("active_source_id",UUID.class),r.getObject("pending_source_id",UUID.class)),tenant,page*20);
    }
    @Transactional(readOnly=true)
    public List<Evidence> search(String tenant,float[] vector,int k) {
        // A shared HNSW graph can under-return after a selective tenant predicate. The
        // local baseline therefore uses an exact scan; approximate mode is opt-in for
        // scale experiments after measuring filtered recall and latency.
        if(exactRetrieval) db.execute("SET LOCAL enable_indexscan=off");
        String encoded=Embeddings.vector(vector);
        return db.query("""
            SELECT c.id,c.source_id,s.title,c.content,1-(c.embedding <=> ?::vector) AS similarity
            FROM chunks c JOIN sources s ON s.id=c.source_id AND s.tenant=c.tenant
            JOIN documents d ON d.id=s.document_id AND d.tenant=s.tenant
            WHERE c.tenant=? AND s.status='READY' AND NOT d.deleted AND d.active_source_id=s.id
            ORDER BY c.embedding <=> ?::vector LIMIT ?
            """,(r,n)->new Evidence(r.getObject("id",UUID.class),r.getObject("source_id",UUID.class),
                r.getString("title"),r.getString("content"),r.getDouble("similarity")),encoded,tenant,encoded,k);
    }
    public void log(Result result,String tenant) {
        db.update("INSERT INTO query_log(id,tenant,latency_ms,cached,input_tokens,output_tokens) VALUES(?,?,?,?,?,?)",
                result.queryId(),tenant,result.latencyMs(),result.cached(),result.inputTokens(),result.outputTokens());
    }
    public UUID feedback(String tenant,Feedback input) {
        UUID id=UUID.randomUUID();
        int count=db.update("""
            INSERT INTO feedback(id,query_id,tenant,rating,comment)
            SELECT ?,id,tenant,?,? FROM query_log WHERE id=? AND tenant=?
            """,id,input.rating(),input.comment(),input.queryId(),tenant);
        if(count==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Query not found");
        return id;
    }
    public UUID escalate(String tenant,UUID sourceId,String reason) {
        get(tenant,sourceId);
        UUID id=TextChunker.id(tenant+"\u0000"+sourceId+"\u0000"+reason);
        db.update("INSERT INTO escalations(id,tenant,source_id,reason) VALUES(?,?,?,?) ON CONFLICT DO NOTHING",id,tenant,sourceId,reason);
        return id;
    }
}
