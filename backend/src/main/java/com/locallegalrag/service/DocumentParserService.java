package com.locallegalrag.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;

@Service
public class DocumentParserService {

    private final Tika tika = new Tika();

    public List<ParsedSection> parse(Path path, String originalFilename) {
        String suffix = suffix(originalFilename);
        try {
            return switch (suffix) {
                case ".pdf" -> parsePdf(path);
                case ".txt", ".md", ".markdown" -> List.of(new ParsedSection(Files.readString(path, StandardCharsets.UTF_8), null));
                case ".docx" -> List.of(new ParsedSection(tika.parseToString(path.toFile()), null));
                default -> throw new BadRequestException("不支持的文件格式：" + originalFilename);
            };
        } catch (IOException exc) {
            throw new IllegalStateException("解析文档失败：" + originalFilename, exc);
        } catch (Exception exc) {
            throw new IllegalStateException("解析文档失败：" + originalFilename + "，文件可能损坏或格式不受支持。", exc);
        }
    }

    private List<ParsedSection> parsePdf(Path path) throws IOException {
        List<ParsedSection> pages = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(path.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(document);
                if (text != null && !text.isBlank()) {
                    pages.add(new ParsedSection(text, page));
                }
            }
        }
        return pages;
    }

    private String suffix(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot).toLowerCase(Locale.ROOT);
    }
}
