package dev.rohit.rag;

import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public class IngestionPipeline {
    private final JdbcTemplate db;
    private final KafkaTemplate<String,String> kafka;
    private final TransactionTemplate transaction;
    private final Embeddings embeddings;
    private final MeterRegistry metrics;
    public IngestionPipeline(JdbcTemplate db,KafkaTemplate<String,String> kafka,TransactionTemplate transaction,
                              Embeddings embeddings,MeterRegistry metrics) {
        this.db=db; this.kafka=kafka; this.transaction=transaction; this.embeddings=embeddings; this.metrics=metrics;
    }
    @Scheduled(fixedDelayString="${app.outbox.delay:2000}")
    public void publish() {
        transaction.executeWithoutResult(status -> {
            var jobs=db.queryForList("SELECT * FROM ingestion_outbox WHERE published=false LIMIT 10 FOR UPDATE SKIP LOCKED");
            for(var job:jobs) {
                try {
                    kafka.send((String)job.get("topic"),job.get("source_id").toString(),job.get("source_id").toString()).get(10,TimeUnit.SECONDS);
                    db.update("UPDATE ingestion_outbox SET published=true WHERE source_id=?",job.get("source_id"));
                } catch(Exception ex) { throw new IllegalStateException("Outbox publish failed; retry on next tick",ex); }
            }
        });
    }
    @KafkaListener(topics={"ingest.docs","ingest.tickets","ingest.runbooks"}, groupId="project1-indexer")
    public void index(String sourceId) {
        metrics.timer("rag.ingestion.duration").record(() -> transaction.executeWithoutResult(status -> {
            UUID id=UUID.fromString(sourceId);
            // All lifecycle operations lock the document before its version, preventing delete/index races.
            var documents=db.queryForList("SELECT d.* FROM documents d JOIN sources s ON s.document_id=d.id WHERE s.id=? FOR UPDATE OF d",id);
            if(documents.isEmpty()) return;
            var document=documents.getFirst();
            if(Boolean.TRUE.equals(document.get("deleted")) || !id.equals(document.get("pending_source_id"))) return;
            var rows=db.queryForList("SELECT * FROM sources WHERE id=? FOR UPDATE",id);
            if(rows.isEmpty()) return;
            var source=rows.getFirst();
            if("READY".equals(source.get("status"))) return;
            String tenant=(String)source.get("tenant");
            var chunks=TextChunker.split((String)source.get("content"));
            for(int i=0;i<chunks.size();i++) {
                String text=chunks.get(i);
                db.update("INSERT INTO chunks(id,source_id,tenant,ordinal,content,embedding) VALUES(?,?,?,?,?,?::vector) ON CONFLICT DO NOTHING",
                    TextChunker.id(id+":"+i),id,tenant,i,text,Embeddings.vector(embeddings.embed(tenant,text)));
            }
            db.update("UPDATE sources SET status='READY',indexed_at=now() WHERE id=?",id);
            db.update("UPDATE documents SET active_source_id=?,pending_source_id=null WHERE id=?",id,source.get("document_id"));
            db.update("INSERT INTO tenant_revision(tenant,revision) VALUES(?,1) ON CONFLICT(tenant) DO UPDATE SET revision=tenant_revision.revision+1",tenant);
        }));
    }
    @KafkaListener(topics={"ingest.docs.DLT","ingest.tickets.DLT","ingest.runbooks.DLT"},groupId="project1-deadletter",
        containerFactory="deadLetterContainerFactory")
    public void deadLetter(String id) {
        transaction.executeWithoutResult(status->{
            var rows=db.queryForList("SELECT d.* FROM documents d JOIN sources s ON s.document_id=d.id WHERE s.id=? FOR UPDATE OF d",UUID.fromString(id));
            if(rows.isEmpty() || Boolean.TRUE.equals(rows.getFirst().get("deleted"))) return;
            db.update("UPDATE sources SET status='FAILED' WHERE id=? AND status='QUEUED'",UUID.fromString(id));
            // Keep a failed initial source retryable. Release a failed replacement so another version can be submitted.
            db.update("UPDATE documents SET pending_source_id=null WHERE pending_source_id=? AND active_source_id IS NOT NULL",UUID.fromString(id));
        });
        metrics.counter("rag.ingestion.failed").increment();
    }
}
