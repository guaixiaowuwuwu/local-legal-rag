package com.locallegalrag.dto;

import java.util.List;

public record QuestionResponse(
        String answer,
        List<SourceResponse> sources,
        List<RetrievedChunkResponse> retrievedChunks
) {
}
