package com.locallegalrag.service;

import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentParserServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void parsesUtf8Markdown() throws Exception {
        Path file = tempDir.resolve("sample.md");
        Files.writeString(file, "# 标题\n\n第二十条 试用期工资");

        var sections = new DocumentParserService().parse(file, "sample.md");

        assertThat(sections).hasSize(1);
        assertThat(sections.get(0).text()).contains("第二十条");
        assertThat(sections.get(0).page()).isNull();
    }
}
