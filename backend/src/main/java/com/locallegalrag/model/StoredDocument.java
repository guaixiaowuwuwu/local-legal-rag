package com.locallegalrag.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record StoredDocument(
        UUID id,
        UUID knowledgeBaseId,
        String originalFilename,
        String storedFilename,
        String contentType,
        long sizeBytes,
        DocumentStatus status,
        String errorMessage,
        OffsetDateTime uploadedAt
) {
}
