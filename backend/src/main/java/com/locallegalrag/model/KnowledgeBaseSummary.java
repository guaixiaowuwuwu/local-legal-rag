package com.locallegalrag.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record KnowledgeBaseSummary(
        UUID id,
        String name,
        String description,
        long documentCount,
        long chunkCount,
        IngestJobStatus latestJobStatus,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
