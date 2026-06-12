package com.locallegalrag.service;

import com.locallegalrag.config.RagProperties;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class LegalTextSplitter {

    private static final String LEGAL_NUMBER = "[零〇一二三四五六七八九十百千万亿两0-9]+(?:之[零〇一二三四五六七八九十百千万亿两0-9]+)?";
    private static final Pattern ARTICLE_RE = Pattern.compile("^\\s*(第" + LEGAL_NUMBER + "条)\\s*([^\\n]*)", Pattern.MULTILINE);
    private static final Pattern HIERARCHY_RE = Pattern.compile("^\\s*(第" + LEGAL_NUMBER + "(?:编|章|节))\\s*([^\\n]*)", Pattern.MULTILINE);
    private static final Pattern SENTENCE_SPLIT_RE = Pattern.compile("(?<=[。；;！？!?])\\s*|\\n{2,}");

    private final RagProperties properties;

    public LegalTextSplitter(RagProperties properties) {
        this.properties = properties;
    }

    public List<TextChunk> split(String text, Map<String, Object> sourceMetadata) {
        String normalized = normalize(text);
        if (normalized.isBlank()) {
            return List.of();
        }
        List<ArticleBlock> articleBlocks = articleBlocks(normalized);
        if (articleBlocks.isEmpty()) {
            return documentsFromParts(splitOversized(normalized), sourceMetadata);
        }
        List<TextChunk> chunks = new ArrayList<>();
        for (ArticleBlock block : articleBlocks) {
            Map<String, Object> metadata = new LinkedHashMap<>(sourceMetadata);
            metadata.putAll(block.metadata());
            chunks.addAll(documentsFromParts(splitOversized(block.text()), metadata));
        }
        return chunks;
    }

    private List<ArticleBlock> articleBlocks(String text) {
        Matcher matcher = ARTICLE_RE.matcher(text);
        List<Match> matches = new ArrayList<>();
        while (matcher.find()) {
            matches.add(new Match(matcher.start(), matcher.group(1), matcher.group(2)));
        }
        List<ArticleBlock> blocks = new ArrayList<>();
        for (int index = 0; index < matches.size(); index++) {
            Match match = matches.get(index);
            int end = index + 1 < matches.size() ? matches.get(index + 1).start() : text.length();
            String block = text.substring(match.start(), end).trim();
            if (block.isBlank()) {
                continue;
            }
            Map<String, Object> metadata = new LinkedHashMap<>(nearestHierarchy(text.substring(0, match.start())));
            metadata.put("article", match.article());
            String title = match.title() == null ? "" : match.title().trim();
            if (!title.isBlank()) {
                metadata.put("articleTitle", title);
            }
            blocks.add(new ArticleBlock(block, metadata));
        }
        return blocks;
    }

    private Map<String, Object> nearestHierarchy(String prefix) {
        Map<String, Object> hierarchy = new HashMap<>();
        Matcher matcher = HIERARCHY_RE.matcher(prefix);
        while (matcher.find()) {
            String label = matcher.group(1);
            String title = (label + " " + matcher.group(2).trim()).trim();
            if (label.endsWith("编")) {
                hierarchy.put("part", title);
            } else if (label.endsWith("章")) {
                hierarchy.put("chapter", title);
            } else if (label.endsWith("节")) {
                hierarchy.put("section", title);
            }
        }
        return hierarchy;
    }

    private List<TextChunk> documentsFromParts(List<String> parts, Map<String, Object> metadata) {
        List<TextChunk> chunks = new ArrayList<>();
        int total = parts.size();
        for (int index = 0; index < parts.size(); index++) {
            String clean = parts.get(index).trim();
            if (clean.isBlank()) {
                continue;
            }
            Map<String, Object> chunkMetadata = new LinkedHashMap<>(metadata);
            chunkMetadata.put("chunkPart", index + 1);
            chunkMetadata.put("chunkParts", total);
            chunks.add(new TextChunk(clean, chunkMetadata));
        }
        return chunks;
    }

    private List<String> splitOversized(String text) {
        int chunkSize = properties.getChunkSize();
        int overlap = Math.max(0, Math.min(properties.getChunkOverlap(), chunkSize / 2));
        if (text.length() <= chunkSize) {
            return List.of(text);
        }

        List<String> units = sentenceUnits(text);
        List<String> chunks = new ArrayList<>();
        String current = "";
        for (String unit : units) {
            if (unit.length() > chunkSize) {
                if (!current.isBlank()) {
                    chunks.add(current.trim());
                    current = "";
                }
                chunks.addAll(windowSplit(unit, chunkSize, overlap));
                continue;
            }
            String candidate = current.isBlank() ? unit : (current + "\n" + unit).trim();
            if (candidate.length() <= chunkSize) {
                current = candidate;
                continue;
            }
            if (!current.isBlank()) {
                chunks.add(current.trim());
                current = joinOverlap(current, unit, overlap);
            } else {
                current = unit;
            }
        }
        if (!current.isBlank()) {
            chunks.add(current.trim());
        }
        return chunks;
    }

    private List<String> sentenceUnits(String text) {
        String[] parts = SENTENCE_SPLIT_RE.split(text);
        List<String> units = new ArrayList<>();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                units.add(part.trim());
            }
        }
        return units.isEmpty() ? List.of(text) : units;
    }

    private List<String> windowSplit(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        int step = Math.max(1, chunkSize - overlap);
        for (int start = 0; start < text.length(); start += step) {
            String chunk = text.substring(start, Math.min(start + chunkSize, text.length())).trim();
            if (!chunk.isBlank()) {
                chunks.add(chunk);
            }
            if (start + chunkSize >= text.length()) {
                break;
            }
        }
        return chunks;
    }

    private String joinOverlap(String previous, String nextUnit, int overlap) {
        if (overlap <= 0) {
            return nextUnit;
        }
        String suffix = previous.substring(Math.max(0, previous.length() - overlap)).trim();
        return (suffix + "\n" + nextUnit).trim();
    }

    private String normalize(String text) {
        return text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    private record Match(int start, String article, String title) {
    }

    private record ArticleBlock(String text, Map<String, Object> metadata) {
    }
}
