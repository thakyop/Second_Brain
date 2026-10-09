package com.Second_Brain.Second_Brain.Document;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class EmbeddingTestController {

    private final EmbeddingService embeddingService;

    public EmbeddingTestController(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    @GetMapping("/api/test/embedding")
    public Map<String, Object> testEmbedding() {
        float[] embedding = embeddingService.embed("Second Brain test document");

        return Map.of(
                "dimensions", embedding.length,
                "firstValue", embedding[0]
        );
    }
}