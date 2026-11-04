package com.Second_Brain.Second_Brain.chat;

import com.Second_Brain.Second_Brain.Document.DocumentChunkRepository;
import com.Second_Brain.Second_Brain.Document.EmbeddingService;
import com.Second_Brain.Second_Brain.Document.RetrievedChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Finds the passages of a workspace's documents that are related to a
 * question: embed the question, then search document_chunks by cosine
 * distance.
 */
@Service
public class RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);

    private final EmbeddingService embeddingService;
    private final DocumentChunkRepository chunkRepository;
    private final int topK;
    private final double maxDistance;
    private final double maxDistanceFromBest;

    public RetrievalService(EmbeddingService embeddingService,
                            DocumentChunkRepository chunkRepository,
                            @Value("${app.chat.top-k}") int topK,
                            @Value("${app.chat.max-distance}") double maxDistance,
                            @Value("${app.chat.max-distance-from-best}") double maxDistanceFromBest) {
        this.embeddingService = embeddingService;
        this.chunkRepository = chunkRepository;
        this.topK = topK;
        this.maxDistance = maxDistance;
        this.maxDistanceFromBest = maxDistanceFromBest;
    }

    /**
     * Up to top-k passages, closest first. A passage counts as related if it
     * is within max-distance of the question, and not much further away than
     * the best match (so loosely related passages don't crowd the answer).
     * Empty if nothing is related.
     */
    public List<RetrievedChunk> findRelevant(UUID workspaceId, String searchText) {
        float[] question = embeddingService.embed(searchText);
        List<RetrievedChunk> closest = chunkRepository.findClosestInWorkspace(workspaceId, question, Limit.of(topK));
        if (closest.isEmpty()) {
            return List.of();
        }
        double cutoff = Math.min(maxDistance, closest.getFirst().distance() + maxDistanceFromBest);
        List<RetrievedChunk> related = closest.stream()
                .filter(chunk -> chunk.distance() <= cutoff)
                .toList();
        // Distances only, never document text.
        log.debug("Retrieval in workspace {}: distances {}, kept {}",
                workspaceId, closest.stream().map(RetrievedChunk::distance).toList(), related.size());
        return related;
    }
}
