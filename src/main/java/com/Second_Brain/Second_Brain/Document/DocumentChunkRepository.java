package com.Second_Brain.Second_Brain.Document;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DocumentChunkRepository
        extends JpaRepository<DocumentChunk, UUID> {

    List<DocumentChunk> findByDocumentIdOrderByChunkIndexAsc(
            UUID documentId
    );

    /*
     * Bulk delete: one DELETE statement that runs immediately.
     *
     * A derived deleteBy... method would remove() each chunk instead,
     * and Hibernate executes those DELETEs at flush time *after* any
     * new INSERTs, so re-ingesting a document would violate
     * uq_document_chunk_index.
     *
     * flushAutomatically writes pending inserts first, so chunks saved
     * earlier in the same transaction are deleted too.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
        delete from DocumentChunk c
        where c.document.id = :documentId
        """)
    void deleteByDocumentId(@Param("documentId") UUID documentId);

    /*
     * Similarity search for chat: the chunks closest in meaning to the
     * question vector, closest first. Only READY documents of the given
     * workspace are searched, so a question can never see another
     * workspace's documents. cosine_distance comes from hibernate-vector
     * (pgvector's <=> operator). No vector index: an exact scan is correct
     * and fast enough for a personal document collection.
     */
    @Query("""
        select new com.Second_Brain.Second_Brain.Document.RetrievedChunk(
            c.id, d.id, d.filename, c.chunkIndex, c.content,
            cosine_distance(c.embedding, :question))
        from DocumentChunk c
        join c.document d
        where d.workspace.id = :workspaceId
          and d.status = com.Second_Brain.Second_Brain.Document.Document.Status.READY
        order by cosine_distance(c.embedding, :question)
        """)
    List<RetrievedChunk> findClosestInWorkspace(
            @Param("workspaceId") UUID workspaceId,
            @Param("question") float[] question,
            Limit limit);
}