CREATE TABLE document_chunks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    document_id UUID NOT NULL
        REFERENCES documents(id)
        ON DELETE CASCADE,

    chunk_index INTEGER NOT NULL
        CHECK (chunk_index >= 0),

    content TEXT NOT NULL,

    embedding vector(768),

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_document_chunk_index
        UNIQUE (document_id, chunk_index)
);

CREATE INDEX idx_document_chunks_document_id
    ON document_chunks(document_id);