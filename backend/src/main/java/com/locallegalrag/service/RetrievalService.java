package com.locallegalrag.service;

import com.locallegalrag.config.RagProperties;
import com.locallegalrag.model.RetrievedChunk;
import com.locallegalrag.repository.RagChunkRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RetrievalService {

    private final RagProperties properties;
    private final KnowledgeBaseService knowledgeBaseService;
    private final EmbeddingService embeddingService;
    private final RagChunkRepository chunkRepository;

    public RetrievalService(
            RagProperties properties,
            KnowledgeBaseService knowledgeBaseService,
            EmbeddingService embeddingService,
            RagChunkRepository chunkRepository
    ) {
        this.properties = properties;
        this.knowledgeBaseService = knowledgeBaseService;
        this.embeddingService = embeddingService;
        this.chunkRepository = chunkRepository;
    }

    public List<RetrievedChunk> search(UUID knowledgeBaseId, String question, Integer requestedTopK) {
        knowledgeBaseService.require(knowledgeBaseId);
        int topK = requestedTopK == null ? properties.getTopK() : Math.max(1, Math.min(requestedTopK, 20));
        List<Double> queryEmbedding = embeddingService.embedQuery(question.strip());
        return chunkRepository.search(knowledgeBaseId, queryEmbedding, topK).stream()
                .filter(chunk -> chunk.score() >= properties.getMinScore())
                .toList();
    }
}
