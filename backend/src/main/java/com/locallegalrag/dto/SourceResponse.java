package com.locallegalrag.dto;

import java.util.UUID;

public record SourceResponse(
        String id,
        UUID chunkId,
        UUID documentId,
        String label,
        String source,
        String sourcePath,
        Integer page,
        String article,
        double score,
        String preview
) {
}
