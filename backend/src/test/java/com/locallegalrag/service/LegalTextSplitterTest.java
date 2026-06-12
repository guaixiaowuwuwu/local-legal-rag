package com.locallegalrag.service;

import com.locallegalrag.config.RagProperties;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LegalTextSplitterTest {

    @Test
    void splitsByChineseLegalArticlesAndKeepsHierarchy() {
        RagProperties properties = new RagProperties();
        LegalTextSplitter splitter = new LegalTextSplitter(properties);
        String text = """
                第一章 劳动合同
                第十九条 试用期
                劳动合同期限三个月以上不满一年的，试用期不得超过一个月。
                第二十条 试用期工资
                劳动者在试用期的工资不得低于本单位相同岗位最低档工资。
                """;

        var chunks = splitter.split(text, Map.of("source", "sample.md"));

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).metadata()).containsEntry("article", "第十九条");
        assertThat(chunks.get(0).metadata()).containsEntry("chapter", "第一章 劳动合同");
        assertThat(chunks.get(1).metadata()).containsEntry("article", "第二十条");
    }

    @Test
    void fallsBackToWindowSplittingForLongText() {
        RagProperties properties = new RagProperties();
        properties.setChunkSize(20);
        properties.setChunkOverlap(4);
        LegalTextSplitter splitter = new LegalTextSplitter(properties);

        var chunks = splitter.split("这是一个没有法条编号但足够长的段落，用来验证窗口切分。", Map.of());

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.content()).hasSizeLessThanOrEqualTo(20));
    }
}
