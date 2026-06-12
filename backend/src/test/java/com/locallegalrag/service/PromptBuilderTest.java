package com.locallegalrag.service;

import com.locallegalrag.model.RetrievedChunk;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PromptBuilderTest {

    @Test
    void buildsLegalPromptWithSourceNumbers() {
        PromptBuilder builder = new PromptBuilder();
        RetrievedChunk chunk = new RetrievedChunk(
                UUID.randomUUID(),
                UUID.randomUUID(),
                0,
                "试用期工资不得低于本单位相同岗位最低档工资。",
                "劳动合同法.md",
                "/docs/劳动合同法.md",
                null,
                "第二十条",
                Map.of("chapter", "第一章 劳动合同"),
                0.91
        );

        String prompt = builder.build("试用期工资有什么要求？", List.of(chunk));

        assertThat(prompt).contains("只允许依据 <检索原文> 中的内容回答");
        assertThat(prompt).contains("[来源1]");
        assertThat(prompt).contains("第二十条");
        assertThat(prompt).contains("试用期工资有什么要求？");
    }

    @Test
    void formatsNoContextMessage() {
        PromptBuilder builder = new PromptBuilder();

        assertThat(builder.formatContext(List.of())).contains(PromptBuilder.NO_CONTEXT_MESSAGE);
    }
}
