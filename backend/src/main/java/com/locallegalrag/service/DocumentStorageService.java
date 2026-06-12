package com.locallegalrag.service;

import com.locallegalrag.config.RagProperties;
import com.locallegalrag.model.StoredDocument;
import com.locallegalrag.repository.DocumentRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentStorageService {

    private static final Set<String> SUPPORTED_SUFFIXES = Set.of(".pdf", ".docx", ".md", ".markdown", ".txt");

    private final RagProperties properties;
    private final DocumentRepository documentRepository;
    private final KnowledgeBaseService knowledgeBaseService;

    public DocumentStorageService(
            RagProperties properties,
            DocumentRepository documentRepository,
            KnowledgeBaseService knowledgeBaseService
    ) {
        this.properties = properties;
        this.documentRepository = documentRepository;
        this.knowledgeBaseService = knowledgeBaseService;
    }

    public List<StoredDocument> list(UUID knowledgeBaseId) {
        knowledgeBaseService.require(knowledgeBaseId);
        return documentRepository.findByKnowledgeBase(knowledgeBaseId);
    }

    public StoredDocument get(UUID knowledgeBaseId, UUID documentId) {
        knowledgeBaseService.require(knowledgeBaseId);
        return documentRepository.findById(knowledgeBaseId, documentId)
                .orElseThrow(() -> new NotFoundException("文档不存在：" + documentId));
    }

    public List<StoredDocument> upload(UUID knowledgeBaseId, List<MultipartFile> files) {
        knowledgeBaseService.require(knowledgeBaseId);
        if (files == null || files.isEmpty()) {
            throw new BadRequestException("请至少上传一个文档。");
        }
        return files.stream().map(file -> storeOne(knowledgeBaseId, file)).toList();
    }

    public void delete(UUID knowledgeBaseId, UUID documentId) {
        StoredDocument document = get(knowledgeBaseId, documentId);
        documentRepository.delete(knowledgeBaseId, documentId);
        try {
            Files.deleteIfExists(resolveStoredPath(document));
        } catch (IOException ignored) {
            // The database row is the source of truth. A missing file is already reflected on next ingest.
        }
    }

    public Path resolveStoredPath(StoredDocument document) {
        return properties.uploadPath()
                .resolve(document.knowledgeBaseId().toString())
                .resolve(document.storedFilename())
                .normalize();
    }

    private StoredDocument storeOne(UUID knowledgeBaseId, MultipartFile file) {
        String original = originalFilename(file);
        validateSuffix(original);
        try {
            Path dir = properties.uploadPath().resolve(knowledgeBaseId.toString());
            Files.createDirectories(dir);
            String storedFilename = UUID.randomUUID() + "-" + sanitize(original);
            Path target = dir.resolve(storedFilename).normalize();
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return documentRepository.create(
                    knowledgeBaseId,
                    original,
                    storedFilename,
                    file.getContentType(),
                    file.getSize()
            );
        } catch (IOException exc) {
            throw new BadRequestException("保存上传文档失败：" + original);
        }
    }

    private String originalFilename(MultipartFile file) {
        String original = file.getOriginalFilename();
        if (original == null || original.isBlank()) {
            throw new BadRequestException("上传文件缺少文件名。");
        }
        return Path.of(original).getFileName().toString();
    }

    private void validateSuffix(String filename) {
        String suffix = suffix(filename);
        if (!SUPPORTED_SUFFIXES.contains(suffix)) {
            throw new BadRequestException("不支持的文件格式：" + filename + "。支持 .pdf、.docx、.md、.txt。");
        }
    }

    private String suffix(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot).toLowerCase(Locale.ROOT);
    }

    private String sanitize(String filename) {
        return filename.replaceAll("[^A-Za-z0-9._\\u4e00-\\u9fff-]", "_");
    }
}
