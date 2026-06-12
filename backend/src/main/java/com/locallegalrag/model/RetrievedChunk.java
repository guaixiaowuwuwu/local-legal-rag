package com.locallegalrag.model;

import java.util.Map;
import java.util.UUID;

public record RetrievedChunk(
        UUID id,
        UUID documentId,
        int chunkIndex,
        String content,
        String source,
        String sourcePath,
        Integer page,
        String article,
        Map<String, Object> metadata,
        double score
) {
}
