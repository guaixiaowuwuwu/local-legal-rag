package com.locallegalrag.service;

import com.locallegalrag.config.RagProperties;
import com.locallegalrag.dto.QuestionResponse;
import com.locallegalrag.dto.RetrievedChunkResponse;
import com.locallegalrag.dto.SourceResponse;
import com.locallegalrag.model.RetrievedChunk;
import com.locallegalrag.repository.RagChunkRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class QuestionService {

    private final RagProperties properties;
    private final KnowledgeBaseService knowledgeBaseService;
    private final EmbeddingService embeddingService;
    private final RagChunkRepository chunkRepository;
    private final PromptBuilder promptBuilder;
    private final ChatService chatService;

    public QuestionService(
            RagProperties properties,
            KnowledgeBaseService knowledgeBaseService,
            EmbeddingService embeddingService,
            RagChunkRepository chunkRepository,
            PromptBuilder promptBuilder,
            ChatService chatService
    ) {
        this.properties = properties;
        this.knowledgeBaseService = knowledgeBaseService;
        this.embeddingService = embeddingService;
        this.chunkRepository = chunkRepository;
        this.promptBuilder = promptBuilder;
        this.chatService = chatService;
    }

    public QuestionResponse ask(UUID knowledgeBaseId, String question, Integer requestedTopK) {
        knowledgeBaseService.require(knowledgeBaseId);
        if (question == null || question.isBlank()) {
            throw new BadRequestException("问题不能为空。");
        }
        int topK = requestedTopK == null ? properties.getTopK() : Math.max(1, Math.min(requestedTopK, 20));
        List<Double> queryEmbedding = embeddingService.embedQuery(question.strip());
        List<RetrievedChunk> chunks = chunkRepository.search(knowledgeBaseId, queryEmbedding, topK);
        if (chunks.isEmpty()) {
            return new QuestionResponse(
                    "结论：" + PromptBuilder.NO_CONTEXT_MESSAGE + "\n依据：无。\n来源：无。",
                    List.of(),
                    List.of()
            );
        }
        String answer = chatService.generate(promptBuilder.build(question, chunks));
        return new QuestionResponse(
                answer,
                sources(chunks),
                chunks.stream().map(this::retrievedChunkResponse).toList()
        );
    }

    private List<SourceResponse> sources(List<RetrievedChunk> chunks) {
        return java.util.stream.IntStream.range(0, chunks.size())
                .mapToObj(index -> {
                    RetrievedChunk chunk = chunks.get(index);
                    return new SourceResponse(
                            "来源" + (index + 1),
                            chunk.id(),
                            chunk.documentId(),
                            promptBuilder.sourceLabel(chunk),
                            chunk.source(),
                            chunk.sourcePath(),
                            chunk.page(),
                            chunk.article(),
                            chunk.score(),
                            preview(chunk.content())
                    );
                })
                .toList();
    }

    private RetrievedChunkResponse retrievedChunkResponse(RetrievedChunk chunk) {
        return new RetrievedChunkResponse(
                chunk.id(),
                chunk.documentId(),
                chunk.chunkIndex(),
                chunk.content(),
                chunk.source(),
                chunk.page(),
                chunk.article(),
                chunk.score()
        );
    }

    private String preview(String content) {
        String clean = content == null ? "" : content.replaceAll("\\s+", " ").trim();
        return clean.length() <= 180 ? clean : clean.substring(0, 180);
    }
}
