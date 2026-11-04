package com.Second_Brain.Second_Brain.Document.service;

import com.Second_Brain.Second_Brain.Document.Document;
import com.Second_Brain.Second_Brain.Document.DocumentChunk;
import com.Second_Brain.Second_Brain.Document.DocumentChunkRepository;
import com.Second_Brain.Second_Brain.Document.DocumentRepository;
import com.Second_Brain.Second_Brain.Document.EmbeddingService;
import com.Second_Brain.Second_Brain.Document.service.TextExtractionService.ExtractionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionOperations;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/*
 * Unit tests: the repositories, text extraction and the embedding model are
 * mocks, and TransactionOperations.withoutTransaction() runs each
 * "transaction" directly, so no database or Ollama is needed.
 */
@ExtendWith(MockitoExtension.class)
class IngestionServiceTest {

    // About 3,300 characters: several chunks at chunk size 1000.
    private static final String LONG_TEXT = "scheduling ".repeat(300);

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentChunkRepository chunkRepository;

    @Mock
    private TextExtractionService extractionService;

    @Mock
    private EmbeddingService embeddingService;

    @Captor
    private ArgumentCaptor<List<DocumentChunk>> savedChunks;

    private final UUID documentId = UUID.randomUUID();
    private Document document;
    private IngestionService ingestionService;

    @BeforeEach
    void setUp() {
        document = new Document();
        document.setId(documentId);
        document.setStatus(Document.Status.UPLOADED);
        ingestionService = new IngestionService(
                documentRepository,
                chunkRepository,
                extractionService,
                new TextChunker(1000, 200),
                embeddingService,
                TransactionOperations.withoutTransaction());
    }

    @Test
    void savesEveryChunkInOrderAndMarksReady() {
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(extractionService.extract(document)).thenReturn(LONG_TEXT);
        when(embeddingService.embed(anyString())).thenReturn(new float[768]);

        ingestionService.processDocument(documentId);

        assertThat(document.getStatus()).isEqualTo(Document.Status.READY);
        InOrder inOrder = inOrder(chunkRepository);
        inOrder.verify(chunkRepository).deleteByDocumentId(documentId); // chunks from an earlier run
        inOrder.verify(chunkRepository).saveAll(savedChunks.capture());
        List<DocumentChunk> chunks = savedChunks.getValue();
        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).extracting(DocumentChunk::getChunkIndex)
                .containsExactlyElementsOf(IntStream.range(0, chunks.size()).boxed().toList());
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.getDocument()).isSameAs(document);
            assertThat(chunk.getContent()).isNotBlank();
            assertThat(chunk.getEmbedding()).hasSize(768);
        });
    }

    @Test
    void documentIsProcessingWhileItIsRead() {
        List<Document.Status> statusDuringExtraction = new ArrayList<>();
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(extractionService.extract(document)).thenAnswer(invocation -> {
            statusDuringExtraction.add(document.getStatus());
            return "Round-robin scheduling";
        });
        when(embeddingService.embed(anyString())).thenReturn(new float[768]);

        ingestionService.processDocument(documentId);

        assertThat(statusDuringExtraction).containsExactly(Document.Status.PROCESSING);
        assertThat(document.getStatus()).isEqualTo(Document.Status.READY);
    }

    @Test
    void uploadEventIngestsThatDocument() {
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(extractionService.extract(document)).thenReturn("Round-robin scheduling");
        when(embeddingService.embed(anyString())).thenReturn(new float[768]);

        ingestionService.onDocumentUploaded(new DocumentUploadedEvent(documentId));

        assertThat(document.getStatus()).isEqualTo(Document.Status.READY);
    }

    @Test
    void documentWithoutTextFails() {
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(extractionService.extract(document)).thenReturn(" \n\t ");

        ingestionService.processDocument(documentId);

        assertThat(document.getStatus()).isEqualTo(Document.Status.FAILED);
        verifyNoInteractions(embeddingService);
        verify(chunkRepository, never()).saveAll(anyList());
    }

    @Test
    void extractionErrorMarksFailedAndLeavesNoChunks() {
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(extractionService.extract(document))
                .thenThrow(new ExtractionException("Failed to extract text from document"));

        ingestionService.processDocument(documentId);

        assertThat(document.getStatus()).isEqualTo(Document.Status.FAILED);
        verify(chunkRepository).deleteByDocumentId(documentId);
        verify(chunkRepository, never()).saveAll(anyList());
    }

    @Test
    void embeddingFailurePartWayThroughSavesNoChunks() {
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(extractionService.extract(document)).thenReturn(LONG_TEXT);
        when(embeddingService.embed(anyString()))
                .thenReturn(new float[768])
                .thenThrow(new IllegalStateException("Ollama is not running"));

        ingestionService.processDocument(documentId);

        assertThat(document.getStatus()).isEqualTo(Document.Status.FAILED);
        verify(chunkRepository, never()).saveAll(anyList());
    }

    @Test
    void failureWhileSavingChunksMarksFailed() {
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(extractionService.extract(document)).thenReturn("Round-robin scheduling");
        when(embeddingService.embed(anyString())).thenReturn(new float[768]);
        when(chunkRepository.saveAll(anyList())).thenThrow(new IllegalStateException("database is down"));

        ingestionService.processDocument(documentId);

        assertThat(document.getStatus()).isEqualTo(Document.Status.FAILED);
    }

    @Test
    void errorsLikeOutOfMemoryAlsoMarkFailed() {
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(extractionService.extract(document)).thenThrow(new OutOfMemoryError("simulated"));

        ingestionService.processDocument(documentId);

        assertThat(document.getStatus()).isEqualTo(Document.Status.FAILED);
    }

    @Test
    void missingDocumentIsSkipped() {
        when(documentRepository.findById(documentId)).thenReturn(Optional.empty());

        ingestionService.processDocument(documentId);

        verifyNoInteractions(extractionService, embeddingService, chunkRepository);
    }
}
