package com.Second_Brain.Second_Brain.Document;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

@Service
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    public EmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public float[] embed(String text) {
        float[] vector = embeddingModel.embed(text);
        if (vector == null || vector.length != 768) {
            throw new IllegalStateException("Embedding vector must have 768 dimensions, got " + (vector == null ? "null" : vector.length));
        }
        return vector;
    }
}