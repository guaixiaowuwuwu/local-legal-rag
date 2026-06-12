package com.locallegalrag.dto;

import java.util.UUID;

public record RetrievedChunkResponse(
        UUID id,
        UUID documentId,
        int chunkIndex,
        String content,
        String source,
        Integer page,
        String article,
        double score
) {
}
