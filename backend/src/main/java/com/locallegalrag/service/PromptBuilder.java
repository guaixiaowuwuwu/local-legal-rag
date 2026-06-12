package com.locallegalrag.service;

import com.locallegalrag.model.RetrievedChunk;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PromptBuilder {

    public static final String NO_CONTEXT_MESSAGE = "本地知识库没有足够依据。未检索到足够相关的法律原文，请补充权威文件后重新建库，或换一种更具体的问法。";

    public String build(String question, List<RetrievedChunk> chunks) {
        return """
                你是律所/法务部门内部的本地法律知识库问答助手。

                必须遵守：
                1. 只允许依据 <检索原文> 中的内容回答。
                2. 不得编造法律条文、案号、发布日期、裁判观点或来源。
                3. 如果检索原文不足以回答，直接说明“本地知识库没有足够依据”。
                4. 回答中必须引用来源编号，例如 [来源1]。
                5. 语言应准确、克制，不能替代律师正式法律意见。

                <检索原文>
                %s
                </检索原文>

                <用户问题>
                %s
                </用户问题>

                请按以下格式输出：
                结论：
                依据：
                来源：
                """.formatted(formatContext(chunks), question.strip());
    }

    public String formatContext(List<RetrievedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return NO_CONTEXT_MESSAGE;
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < chunks.size(); index++) {
            RetrievedChunk chunk = chunks.get(index);
            if (!builder.isEmpty()) {
                builder.append("\n\n");
            }
            builder.append("[来源").append(index + 1).append("] ")
                    .append(sourceLabel(chunk))
                    .append('\n')
                    .append(chunk.content().strip());
        }
        return builder.toString();
    }

    public String sourceLabel(RetrievedChunk chunk) {
        StringBuilder builder = new StringBuilder(chunk.source());
        if (chunk.page() != null) {
            builder.append(" | 第").append(chunk.page()).append("页");
        }
        Object part = chunk.metadata().get("part");
        Object chapter = chunk.metadata().get("chapter");
        Object section = chunk.metadata().get("section");
        if (part != null) {
            builder.append(" | ").append(part);
        }
        if (chapter != null) {
            builder.append(" | ").append(chapter);
        }
        if (section != null) {
            builder.append(" | ").append(section);
        }
        if (chunk.article() != null && !chunk.article().isBlank()) {
            builder.append(" | ").append(chunk.article());
        }
        builder.append(" | score=").append("%.4f".formatted(chunk.score()));
        return builder.toString();
    }
}
