package com.locallegalrag.service;

import com.locallegalrag.model.IngestJob;
import com.locallegalrag.model.RagChunk;
import com.locallegalrag.model.StoredDocument;
import com.locallegalrag.repository.DocumentRepository;
import com.locallegalrag.repository.IngestJobRepository;
import com.locallegalrag.repository.RagChunkRepository;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import static com.locallegalrag.model.DocumentStatus.FAILED;
import static com.locallegalrag.model.DocumentStatus.INDEXED;

@Service
public class IngestService {

    private final KnowledgeBaseService knowledgeBaseService;
    private final DocumentStorageService documentStorageService;
    private final DocumentRepository documentRepository;
    private final IngestJobRepository jobRepository;
    private final RagChunkRepository chunkRepository;
    private final DocumentParserService parserService;
    private final LegalTextSplitter splitter;
    private final EmbeddingService embeddingService;
    private final TaskExecutor taskExecutor;

    public IngestService(
            KnowledgeBaseService knowledgeBaseService,
            DocumentStorageService documentStorageService,
            DocumentRepository documentRepository,
            IngestJobRepository jobRepository,
            RagChunkRepository chunkRepository,
            DocumentParserService parserService,
            LegalTextSplitter splitter,
            EmbeddingService embeddingService,
            TaskExecutor taskExecutor
    ) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.documentStorageService = documentStorageService;
        this.documentRepository = documentRepository;
        this.jobRepository = jobRepository;
        this.chunkRepository = chunkRepository;
        this.parserService = parserService;
        this.splitter = splitter;
        this.embeddingService = embeddingService;
        this.taskExecutor = taskExecutor;
    }

    public IngestJob createJob(UUID knowledgeBaseId, boolean resetIndex) {
        knowledgeBaseService.require(knowledgeBaseId);
        IngestJob job = jobRepository.create(knowledgeBaseId, resetIndex);
        taskExecutor.execute(() -> run(job.id()));
        return job;
    }

    public IngestJob getJob(UUID jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new NotFoundException("建库任务不存在：" + jobId));
    }

    void run(UUID jobId) {
        IngestJob job = getJob(jobId);
        List<StoredDocument> documents = documentStorageService.list(job.knowledgeBaseId());
        jobRepository.start(jobId, documents.size());
        try {
            if (job.resetIndex()) {
                chunkRepository.deleteByKnowledgeBase(job.knowledgeBaseId());
            }
            int processed = 0;
            int totalChunks = 0;
            for (StoredDocument document : documents) {
                int chunkCount = ingestDocument(job.knowledgeBaseId(), document);
                totalChunks += chunkCount;
                processed += 1;
                jobRepository.progress(jobId, processed, totalChunks);
            }
            jobRepository.finish(jobId, processed, totalChunks);
        } catch (Exception exc) {
            jobRepository.fail(jobId, compact(exc.getMessage()));
        }
    }

    private int ingestDocument(UUID knowledgeBaseId, StoredDocument document) {
        Path path = documentStorageService.resolveStoredPath(document);
        try {
            chunkRepository.deleteByDocument(knowledgeBaseId, document.id());
            List<RagChunk> chunks = buildChunks(knowledgeBaseId, document, path);
            if (chunks.isEmpty()) {
                documentRepository.markStatus(document.id(), FAILED, "文档已解析，但没有可入库文本。");
                return 0;
            }
            List<List<Double>> embeddings = embeddingService.embedDocuments(
                    chunks.stream().map(RagChunk::content).toList()
            );
            for (int index = 0; index < chunks.size(); index++) {
                chunkRepository.save(chunks.get(index), embeddings.get(index));
            }
            documentRepository.markStatus(document.id(), INDEXED, null);
            return chunks.size();
        } catch (Exception exc) {
            documentRepository.markStatus(document.id(), FAILED, compact(exc.getMessage()));
            throw exc;
        }
    }

    private List<RagChunk> buildChunks(UUID knowledgeBaseId, StoredDocument document, Path path) {
        List<RagChunk> chunks = new ArrayList<>();
        List<ParsedSection> sections = parserService.parse(path, document.originalFilename());
        int chunkIndex = 0;
        for (ParsedSection section : sections) {
            Map<String, Object> metadata = baseMetadata(document, path, section.page());
            for (TextChunk textChunk : splitter.split(section.text(), metadata)) {
                Map<String, Object> chunkMetadata = new LinkedHashMap<>(textChunk.metadata());
                String article = asString(chunkMetadata.get("article"));
                chunks.add(new RagChunk(
                        UUID.randomUUID(),
                        knowledgeBaseId,
                        document.id(),
                        chunkIndex,
                        textChunk.content(),
                        document.originalFilename(),
                        path.toString(),
                        section.page(),
                        article,
                        chunkMetadata
                ));
                chunkIndex += 1;
            }
        }
        return chunks;
    }

    private Map<String, Object> baseMetadata(StoredDocument document, Path path, Integer page) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", document.originalFilename());
        metadata.put("sourcePath", path.toString());
        metadata.put("fileName", document.originalFilename());
        if (page != null) {
            metadata.put("page", page);
        }
        return metadata;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String compact(String message) {
        String clean = message == null ? "建库失败。" : message.replaceAll("\\s+", " ").trim();
        return clean.length() <= 600 ? clean : clean.substring(0, 599) + "...";
    }
}
