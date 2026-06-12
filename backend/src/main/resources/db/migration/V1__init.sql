create extension if not exists vector;
create extension if not exists pgcrypto;

create table if not exists knowledge_bases (
    id uuid primary key default gen_random_uuid(),
    name text not null,
    description text not null default '',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists documents (
    id uuid primary key default gen_random_uuid(),
    knowledge_base_id uuid not null references knowledge_bases(id) on delete cascade,
    original_filename text not null,
    stored_filename text not null,
    content_type text,
    size_bytes bigint not null,
    status text not null default 'UPLOADED',
    error_message text,
    uploaded_at timestamptz not null default now()
);

create table if not exists ingest_jobs (
    id uuid primary key default gen_random_uuid(),
    knowledge_base_id uuid not null references knowledge_bases(id) on delete cascade,
    status text not null,
    reset_index boolean not null default true,
    total_documents integer not null default 0,
    processed_documents integer not null default 0,
    total_chunks integer not null default 0,
    error_message text,
    created_at timestamptz not null default now(),
    started_at timestamptz,
    finished_at timestamptz
);

create table if not exists rag_chunks (
    id uuid primary key default gen_random_uuid(),
    knowledge_base_id uuid not null references knowledge_bases(id) on delete cascade,
    document_id uuid not null references documents(id) on delete cascade,
    chunk_index integer not null,
    content text not null,
    source text not null,
    source_path text not null,
    page integer,
    article text,
    metadata jsonb not null default '{}'::jsonb,
    embedding vector(1536) not null,
    created_at timestamptz not null default now()
);

create index if not exists idx_documents_kb on documents(knowledge_base_id);
create index if not exists idx_ingest_jobs_kb_created on ingest_jobs(knowledge_base_id, created_at desc);
create index if not exists idx_rag_chunks_kb on rag_chunks(knowledge_base_id);
create index if not exists idx_rag_chunks_document on rag_chunks(document_id);
create index if not exists idx_rag_chunks_embedding_hnsw
    on rag_chunks using hnsw (embedding vector_cosine_ops);
