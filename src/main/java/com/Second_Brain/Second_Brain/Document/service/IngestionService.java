package com.Second_Brain.Second_Brain.Document.service;

import com.Second_Brain.Second_Brain.Document.Document;
import com.Second_Brain.Second_Brain.Document.DocumentChunk;
import com.Second_Brain.Second_Brain.Document.DocumentChunkRepository;
import com.Second_Brain.Second_Brain.Document.DocumentRepository;
import com.Second_Brain.Second_Brain.Document.EmbeddingService;
import com.Second_Brain.Second_Brain.Document.service.TextExtractionService.ExtractionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionOperations;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Orchestrates the full ingestion pipeline for a newly uploaded document:
 * extract the text, split it into chunks, embed every chunk, save the chunks.
 *
 * The slow work (reading the file, calling the embedding model) runs outside
 * any database transaction. Only three short steps use one, so that:
 *   - clients can see PROCESSING while a document is being ingested,
 *   - no database connection is held while waiting for the model, and
 *   - the chunks are saved all-or-nothing, together with the READY status.
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final TextExtractionService extractionService;
    private final TextChunker chunker;
    private final EmbeddingService embeddingService;
    private final TransactionOperations transactions;

    public IngestionService(DocumentRepository documentRepository,
                            DocumentChunkRepository chunkRepository,
                            TextExtractionService extractionService,
                            TextChunker chunker,
                            EmbeddingService embeddingService,
                            TransactionOperations transactions) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.extractionService = extractionService;
        this.chunker = chunker;
        this.embeddingService = embeddingService;
        this.transactions = transactions;
    }

    /**
     * Runs on a background thread once the upload's transaction has committed.
     * Starting earlier could look for the document before its row is visible.
     */
    @Async
    @TransactionalEventListener
    public void onDocumentUploaded(DocumentUploadedEvent event) {
        processDocument(event.documentId());
    }

    /**
     * Ingests one document and leaves it READY or FAILED. Running it again for
     * the same document is safe: the old chunks are replaced.
     */
    public void processDocument(UUID documentId) {
        Document document = transactions.execute(status -> markProcessing(documentId));
        if (document == null) {
            log.warn("Document {} no longer exists; nothing to ingest", documentId);
            return;
        }
        try {
            String text = extractionService.extract(document);
            List<String> chunks = chunker.chunk(text);
            if (chunks.isEmpty()) {
                // e.g. a scanned PDF with no text layer: nothing would be searchable
                throw new ExtractionException("No text found in document");
            }
            List<float[]> embeddings = new ArrayList<>(chunks.size());
            for (String chunk : chunks) {
                embeddings.add(embeddingService.embed(chunk));
            }
            transactions.executeWithoutResult(status -> saveChunks(documentId, chunks, embeddings));
            log.info("Ingested document {} into {} chunks", documentId, chunks.size());
        } catch (Exception | Error e) {
            // Error too: an OutOfMemoryError must not leave the document stuck.
            markFailed(documentId, e);
        }
    }

    // Returns null if the document no longer exists.
    private Document markProcessing(UUID documentId) {
        Document document = documentRepository.findById(documentId).orElse(null);
        if (document != null) {
            // A managed entity: the change is saved when the transaction commits.
            document.setStatus(Document.Status.PROCESSING);
        }
        return document;
    }

    private void saveChunks(UUID documentId, List<String> chunks, List<float[]> embeddings) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalStateException("Document was deleted during ingestion"));
        chunkRepository.deleteByDocumentId(documentId); // chunks from an earlier run
        List<DocumentChunk> entities = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk chunk = new DocumentChunk();
            chunk.setDocument(document);
            chunk.setChunkIndex(i);
            chunk.setContent(chunks.get(i));
            chunk.setEmbedding(embeddings.get(i));
            entities.add(chunk);
        }
        chunkRepository.saveAll(entities);
        // Same transaction as the chunks: READY only if every chunk was saved.
        document.setStatus(Document.Status.READY);
    }

    private void markFailed(UUID documentId, Throwable cause) {
        // Logs the full stack trace, including the underlying cause.
        log.error("Ingestion failed for document {}", documentId, cause);
        try {
            transactions.executeWithoutResult(status ->
                    documentRepository.findById(documentId).ifPresent(document -> {
                        chunkRepository.deleteByDocumentId(documentId);
                        document.setStatus(Document.Status.FAILED);
                    }));
        } catch (RuntimeException e) {
            log.error("Could not mark document {} as FAILED", documentId, e);
        }
    }
}
