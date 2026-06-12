package com.locallegalrag.service;

import com.locallegalrag.model.RetrievedChunk;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestionServiceTest {

    private final RetrievalService retrievalService = mock(RetrievalService.class);
    private final PromptBuilder promptBuilder = new PromptBuilder();
    private final ChatService chatService = mock(ChatService.class);

    @Test
    void returnsNoContextAnswerWhenRetrievalFindsNoChunks() {
        UUID knowledgeBaseId = UUID.randomUUID();
        when(retrievalService.search(knowledgeBaseId, "试用期工资是什么？", 8)).thenReturn(List.of());
        QuestionService service = new QuestionService(retrievalService, promptBuilder, chatService);

        var response = service.ask(knowledgeBaseId, "试用期工资是什么？", 8);

        assertThat(response.answer())
                .contains("本地知识库没有足够依据")
                .contains(PromptBuilder.NO_CONTEXT_MESSAGE);
        assertThat(response.sources()).isEmpty();
        assertThat(response.retrievedChunks()).isEmpty();
        verify(chatService, never()).generate(anyString());
    }

    @Test
    void keepsNumberedSourcesWhenChunksAreRetrieved() {
        UUID knowledgeBaseId = UUID.randomUUID();
        RetrievedChunk chunk = new RetrievedChunk(
                UUID.randomUUID(),
                UUID.randomUUID(),
                2,
                "劳动者在试用期的工资不得低于本单位相同岗位最低档工资。",
                "劳动合同法摘录",
                "data/documents/formal/labor/national_law/laodong_hetong_fa_excerpt.md",
                null,
                "第二十条",
                Map.of(),
                0.93
        );
        when(retrievalService.search(knowledgeBaseId, "试用期工资是什么？", null)).thenReturn(List.of(chunk));
        when(chatService.generate(anyString())).thenReturn("结论：应引用 [来源1]。\n依据：...\n来源：[来源1]");
        QuestionService service = new QuestionService(retrievalService, promptBuilder, chatService);

        var response = service.ask(knowledgeBaseId, "试用期工资是什么？", null);

        assertThat(response.answer()).contains("[来源1]");
        assertThat(response.sources()).hasSize(1);
        assertThat(response.sources().get(0).id()).isEqualTo("来源1");
        assertThat(response.sources().get(0).sourcePath())
                .isEqualTo("data/documents/formal/labor/national_law/laodong_hetong_fa_excerpt.md");
    }
}
