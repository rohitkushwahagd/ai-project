CREATE EXTENSION IF NOT EXISTS vector;
CREATE TABLE sources (
 id uuid PRIMARY KEY, tenant varchar(80) NOT NULL, kind varchar(20) NOT NULL,
 title varchar(120) NOT NULL, content text NOT NULL, status varchar(20) NOT NULL DEFAULT 'QUEUED',
 created_at timestamptz NOT NULL DEFAULT now(), indexed_at timestamptz
);
CREATE INDEX sources_tenant ON sources(tenant, id);
CREATE TABLE ingestion_outbox (
 source_id uuid PRIMARY KEY REFERENCES sources(id), topic varchar(100) NOT NULL,
 published boolean NOT NULL DEFAULT false
);
CREATE TABLE chunks (
 id uuid PRIMARY KEY, source_id uuid NOT NULL REFERENCES sources(id), tenant varchar(80) NOT NULL,
 ordinal integer NOT NULL, content text NOT NULL, embedding vector(768) NOT NULL,
 UNIQUE(source_id, ordinal)
);
CREATE INDEX chunks_tenant ON chunks(tenant);
CREATE INDEX chunks_embedding ON chunks USING hnsw (embedding vector_cosine_ops);
CREATE TABLE tenant_revision (tenant varchar(80) PRIMARY KEY, revision bigint NOT NULL DEFAULT 0);
CREATE TABLE query_log (
 id uuid PRIMARY KEY, tenant varchar(80) NOT NULL, created_at timestamptz NOT NULL DEFAULT now(),
 latency_ms bigint NOT NULL, cached boolean NOT NULL, input_tokens bigint, output_tokens bigint
);
CREATE TABLE feedback (
 id uuid PRIMARY KEY, query_id uuid NOT NULL REFERENCES query_log(id), tenant varchar(80) NOT NULL,
 rating integer NOT NULL CHECK(rating BETWEEN 1 AND 5), comment varchar(2000), created_at timestamptz DEFAULT now()
);
CREATE TABLE escalations (
 id uuid PRIMARY KEY, tenant varchar(80) NOT NULL, source_id uuid NOT NULL REFERENCES sources(id),
 reason varchar(1000) NOT NULL, created_at timestamptz DEFAULT now(),
 UNIQUE(tenant, source_id, reason)
);
