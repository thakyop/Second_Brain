package com.Second_Brain.Second_Brain.chat;

import com.Second_Brain.Second_Brain.Document.DocumentChunkRepository;
import com.Second_Brain.Second_Brain.Document.EmbeddingService;
import com.Second_Brain.Second_Brain.Document.RetrievedChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetrievalServiceTest {

    private static final UUID WORKSPACE = UUID.randomUUID();
    private static final float[] QUESTION = new float[768];

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private DocumentChunkRepository chunkRepository;

    private RetrievalService retrievalService;

    @BeforeEach
    void setUp() {
        // top-k 5, max distance 0.5, at most 0.1 further than the best match
        retrievalService = new RetrievalService(embeddingService, chunkRepository, 5, 0.5, 0.1);
        when(embeddingService.embed("What is round-robin?")).thenReturn(QUESTION);
    }

    @Test
    void keepsPassagesCloseToTheQuestionAndToTheBestMatch() {
        searchFinds(0.26, 0.33, 0.37, 0.45);

        assertThat(retrievalService.findRelevant(WORKSPACE, "What is round-robin?"))
                .extracting(RetrievedChunk::distance)
                .containsExactly(0.26, 0.33);
    }

    @Test
    void dropsPassagesBeyondTheMaximumDistance() {
        searchFinds(0.45, 0.49, 0.52);

        assertThat(retrievalService.findRelevant(WORKSPACE, "What is round-robin?"))
                .extracting(RetrievedChunk::distance)
                .containsExactly(0.45, 0.49);
    }

    @Test
    void findsNothingWhenEvenTheBestMatchIsUnrelated() {
        searchFinds(0.55, 0.60);

        assertThat(retrievalService.findRelevant(WORKSPACE, "What is round-robin?")).isEmpty();
    }

    @Test
    void findsNothingInAWorkspaceWithoutChunks() {
        searchFinds();

        assertThat(retrievalService.findRelevant(WORKSPACE, "What is round-robin?")).isEmpty();
    }

    private void searchFinds(double... distances) {
        List<RetrievedChunk> chunks = Arrays.stream(distances)
                .mapToObj(d -> new RetrievedChunk(UUID.randomUUID(), UUID.randomUUID(), "notes.md", 0, "text", d))
                .toList();
        when(chunkRepository.findClosestInWorkspace(WORKSPACE, QUESTION, Limit.of(5))).thenReturn(chunks);
    }
}
