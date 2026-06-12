package com.locallegalrag.dto;

public record RuntimeConfigResponse(
        String apiBaseUrl,
        String chatModel,
        String embeddingModel,
        int embeddingDimensions,
        int chunkSize,
        int chunkOverlap,
        int topK,
        boolean apiKeyConfigured
) {
}
