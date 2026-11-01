-- A logical document owns immutable source versions. Only its active version is searchable.
ALTER TABLE sources ADD COLUMN document_id uuid;
UPDATE sources SET document_id=id;
ALTER TABLE sources ALTER COLUMN document_id SET NOT NULL;
ALTER TABLE sources ADD CONSTRAINT sources_tenant_id_unique UNIQUE (tenant,id);
CREATE TABLE documents (
 id uuid PRIMARY KEY,
 tenant varchar(80) NOT NULL,
 active_source_id uuid,
 pending_source_id uuid,
 deleted boolean NOT NULL DEFAULT false,
 UNIQUE(tenant,id),
 FOREIGN KEY(tenant,active_source_id) REFERENCES sources(tenant,id),
 FOREIGN KEY(tenant,pending_source_id) REFERENCES sources(tenant,id)
);
INSERT INTO documents(id,tenant,active_source_id,pending_source_id)
 SELECT id,tenant,CASE WHEN status='READY' THEN id END,
 CASE WHEN status<>'READY' THEN id END FROM sources;
ALTER TABLE sources ADD CONSTRAINT sources_document_fk
 FOREIGN KEY(tenant,document_id) REFERENCES documents(tenant,id) DEFERRABLE INITIALLY DEFERRED;
ALTER TABLE chunks ADD CONSTRAINT chunks_tenant_source_fk
 FOREIGN KEY(tenant,source_id) REFERENCES sources(tenant,id);
CREATE TABLE ingestion_requests (
 tenant varchar(80) NOT NULL,
 request_key varchar(160) NOT NULL,
 payload_hash uuid NOT NULL,
 source_id uuid NOT NULL,
 PRIMARY KEY(tenant,request_key),
 FOREIGN KEY(tenant,source_id) REFERENCES sources(tenant,id)
);
