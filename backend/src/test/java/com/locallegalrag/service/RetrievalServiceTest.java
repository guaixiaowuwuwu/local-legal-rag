package com.locallegalrag.service;

import com.locallegalrag.config.RagProperties;
import com.locallegalrag.model.KnowledgeBase;
import com.locallegalrag.model.RetrievedChunk;
import com.locallegalrag.repository.RagChunkRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RetrievalServiceTest {

    private final RagProperties properties = new RagProperties();
    private final KnowledgeBaseService knowledgeBaseService = mock(KnowledgeBaseService.class);
    private final EmbeddingService embeddingService = mock(EmbeddingService.class);
    private final RagChunkRepository chunkRepository = mock(RagChunkRepository.class);

    @Test
    void keepsChunksAtOrAboveMinScore() {
        UUID knowledgeBaseId = UUID.randomUUID();
        properties.setMinScore(0.8);
        when(knowledgeBaseService.require(knowledgeBaseId)).thenReturn(knowledgeBase(knowledgeBaseId));
        when(embeddingService.embedQuery("试用期")).thenReturn(List.of(0.1, 0.2));
        RetrievedChunk high = chunkWithScore(0.91);
        RetrievedChunk equal = chunkWithScore(0.8);
        RetrievedChunk low = chunkWithScore(0.79);
        when(chunkRepository.search(knowledgeBaseId, List.of(0.1, 0.2), 8))
                .thenReturn(List.of(high, equal, low));

        RetrievalService service = new RetrievalService(
                properties,
                knowledgeBaseService,
                embeddingService,
                chunkRepository
        );

        List<RetrievedChunk> chunks = service.search(knowledgeBaseId, " 试用期 ", 8);

        assertThat(chunks).containsExactly(high, equal);
        verify(chunkRepository).search(knowledgeBaseId, List.of(0.1, 0.2), 8);
    }

    @Test
    void usesConfiguredTopKWhenRequestDoesNotProvideOne() {
        UUID knowledgeBaseId = UUID.randomUUID();
        properties.setTopK(7);
        when(knowledgeBaseService.require(knowledgeBaseId)).thenReturn(knowledgeBase(knowledgeBaseId));
        when(embeddingService.embedQuery("经济补偿")).thenReturn(List.of(0.3, 0.4));
        when(chunkRepository.search(knowledgeBaseId, List.of(0.3, 0.4), 7)).thenReturn(List.of());

        RetrievalService service = new RetrievalService(
                properties,
                knowledgeBaseService,
                embeddingService,
                chunkRepository
        );

        List<RetrievedChunk> chunks = service.search(knowledgeBaseId, "经济补偿", null);

        assertThat(chunks).isEmpty();
        verify(chunkRepository).search(knowledgeBaseId, List.of(0.3, 0.4), 7);
    }

    private KnowledgeBase knowledgeBase(UUID id) {
        return new KnowledgeBase(id, "劳动法规", "", OffsetDateTime.now(), OffsetDateTime.now());
    }

    private RetrievedChunk chunkWithScore(double score) {
        return new RetrievedChunk(
                UUID.randomUUID(),
                UUID.randomUUID(),
                0,
                "劳动法律原文",
                "劳动合同法.md",
                "data/documents/formal/labor/national_law/laodong_hetong_fa_excerpt.md",
                null,
                "第十九条",
                Map.of(),
                score
        );
    }
}
