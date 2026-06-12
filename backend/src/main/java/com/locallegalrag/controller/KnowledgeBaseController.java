package com.locallegalrag.controller;

import com.locallegalrag.dto.AskQuestionRequest;
import com.locallegalrag.dto.CreateIngestJobRequest;
import com.locallegalrag.dto.CreateKnowledgeBaseRequest;
import com.locallegalrag.dto.QuestionResponse;
import com.locallegalrag.dto.RuntimeConfigResponse;
import com.locallegalrag.model.IngestJob;
import com.locallegalrag.model.KnowledgeBase;
import com.locallegalrag.model.KnowledgeBaseSummary;
import com.locallegalrag.model.StoredDocument;
import com.locallegalrag.service.AiSettings;
import com.locallegalrag.config.RagProperties;
import com.locallegalrag.service.DocumentStorageService;
import com.locallegalrag.service.IngestService;
import com.locallegalrag.service.KnowledgeBaseService;
import com.locallegalrag.service.QuestionService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;
    private final DocumentStorageService documentStorageService;
    private final IngestService ingestService;
    private final QuestionService questionService;
    private final RagProperties properties;
    private final AiSettings aiSettings;

    public KnowledgeBaseController(
            KnowledgeBaseService knowledgeBaseService,
            DocumentStorageService documentStorageService,
            IngestService ingestService,
            QuestionService questionService,
            RagProperties properties,
            AiSettings aiSettings
    ) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.documentStorageService = documentStorageService;
        this.ingestService = ingestService;
        this.questionService = questionService;
        this.properties = properties;
        this.aiSettings = aiSettings;
    }

    @GetMapping("/knowledge-bases")
    public List<KnowledgeBaseSummary> listKnowledgeBases() {
        return knowledgeBaseService.list();
    }

    @PostMapping("/knowledge-bases")
    @ResponseStatus(HttpStatus.CREATED)
    public KnowledgeBase createKnowledgeBase(@Valid @RequestBody CreateKnowledgeBaseRequest request) {
        return knowledgeBaseService.create(request);
    }

    @GetMapping("/knowledge-bases/{id}")
    public KnowledgeBaseSummary getKnowledgeBase(@PathVariable UUID id) {
        return knowledgeBaseService.getSummary(id);
    }

    @GetMapping("/knowledge-bases/{id}/documents")
    public List<StoredDocument> listDocuments(@PathVariable UUID id) {
        return documentStorageService.list(id);
    }

    @PostMapping("/knowledge-bases/{id}/documents")
    @ResponseStatus(HttpStatus.CREATED)
    public List<StoredDocument> uploadDocuments(
            @PathVariable UUID id,
            @RequestParam("files") List<MultipartFile> files
    ) {
        return documentStorageService.upload(id, files);
    }

    @GetMapping("/knowledge-bases/{id}/documents/{documentId}")
    public StoredDocument getDocument(@PathVariable UUID id, @PathVariable UUID documentId) {
        return documentStorageService.get(id, documentId);
    }

    @DeleteMapping("/knowledge-bases/{id}/documents/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDocument(@PathVariable UUID id, @PathVariable UUID documentId) {
        documentStorageService.delete(id, documentId);
    }

    @PostMapping("/knowledge-bases/{id}/ingest-jobs")
    @ResponseStatus(HttpStatus.CREATED)
    public IngestJob createIngestJob(
            @PathVariable UUID id,
            @RequestBody(required = false) CreateIngestJobRequest request
    ) {
        boolean resetIndex = request == null || request.resolvedResetIndex();
        return ingestService.createJob(id, resetIndex);
    }

    @GetMapping("/ingest-jobs/{jobId}")
    public IngestJob getIngestJob(@PathVariable UUID jobId) {
        return ingestService.getJob(jobId);
    }

    @PostMapping("/knowledge-bases/{id}/questions")
    public QuestionResponse ask(@PathVariable UUID id, @Valid @RequestBody AskQuestionRequest request) {
        return questionService.ask(id, request.question(), request.topK());
    }

    @GetMapping("/runtime-config")
    public RuntimeConfigResponse runtimeConfig() {
        return new RuntimeConfigResponse(
                aiSettings.baseUrl(),
                aiSettings.chatModel(),
                aiSettings.embeddingModel(),
                properties.getEmbeddingDimensions(),
                properties.getChunkSize(),
                properties.getChunkOverlap(),
                properties.getTopK(),
                aiSettings.apiKeyConfigured()
        );
    }
}
