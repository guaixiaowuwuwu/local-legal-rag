package com.locallegalrag.service;

import com.locallegalrag.dto.CreateKnowledgeBaseRequest;
import com.locallegalrag.model.KnowledgeBase;
import com.locallegalrag.model.KnowledgeBaseSummary;
import com.locallegalrag.repository.KnowledgeBaseRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeBaseService {

    private final KnowledgeBaseRepository repository;

    public KnowledgeBaseService(KnowledgeBaseRepository repository) {
        this.repository = repository;
    }

    public List<KnowledgeBaseSummary> list() {
        return repository.findAllSummaries();
    }

    public KnowledgeBaseSummary getSummary(UUID id) {
        return repository.findSummary(id)
                .orElseThrow(() -> new NotFoundException("知识库不存在：" + id));
    }

    public KnowledgeBase require(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("知识库不存在：" + id));
    }

    public KnowledgeBase create(CreateKnowledgeBaseRequest request) {
        return repository.create(request.name(), request.description());
    }
}
