package com.locallegalrag.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record KnowledgeBase(
        UUID id,
        String name,
        String description,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
