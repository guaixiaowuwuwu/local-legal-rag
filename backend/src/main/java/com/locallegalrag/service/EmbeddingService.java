package com.locallegalrag.service;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class EmbeddingService {

    private static final int BATCH_SIZE = 16;

    private final OpenAiCompatibleClient client;

    public EmbeddingService(OpenAiCompatibleClient client) {
        this.client = client;
    }

    public List<Double> embedQuery(String text) {
        return client.embed(List.of(text)).get(0);
    }

    public List<List<Double>> embedDocuments(List<String> texts) {
        List<List<Double>> vectors = new ArrayList<>();
        for (int start = 0; start < texts.size(); start += BATCH_SIZE) {
            int end = Math.min(start + BATCH_SIZE, texts.size());
            vectors.addAll(client.embed(texts.subList(start, end)));
        }
        return vectors;
    }
}
