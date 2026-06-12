package com.locallegalrag.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record IngestJob(
        UUID id,
        UUID knowledgeBaseId,
        IngestJobStatus status,
        boolean resetIndex,
        int totalDocuments,
        int processedDocuments,
        int totalChunks,
        String errorMessage,
        OffsetDateTime createdAt,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt
) {
}
