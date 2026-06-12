package com.locallegalrag.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OpenAiCompatibleClient {

    private final AiSettings settings;
    private final RestClient restClient;

    public OpenAiCompatibleClient(AiSettings settings, RestClient.Builder restClientBuilder) {
        this.settings = settings;
        this.restClient = restClientBuilder
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().setBearerAuth(requireApiKey());
                    return execution.execute(request, body);
                })
                .build();
    }

    public List<List<Double>> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        Map<String, Object> payload = Map.of(
                "model", settings.embeddingModel(),
                "input", texts
        );
        JsonNode response = postJson("/embeddings", payload);
        JsonNode data = response.get("data");
        if (data == null || !data.isArray()) {
            throw new IllegalStateException("Embedding API 返回格式异常：缺少 data 数组。");
        }
        List<JsonNode> items = new ArrayList<>();
        data.forEach(items::add);
        items.sort(Comparator.comparingInt(item -> item.path("index").asInt(items.indexOf(item))));
        List<List<Double>> vectors = new ArrayList<>();
        for (JsonNode item : items) {
            JsonNode embedding = item.get("embedding");
            if (embedding == null || !embedding.isArray()) {
                throw new IllegalStateException("Embedding API 返回格式异常：缺少 embedding 数组。");
            }
            List<Double> vector = new ArrayList<>();
            embedding.forEach(value -> vector.add(value.asDouble()));
            vectors.add(vector);
        }
        if (vectors.size() != texts.size()) {
            throw new IllegalStateException("Embedding API 返回数量与请求数量不一致。");
        }
        return vectors;
    }

    public String chat(String prompt) {
        Map<String, Object> message = Map.of("role", "user", "content", prompt);
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", settings.chatModel());
        payload.put("messages", List.of(message));
        payload.put("temperature", 0.1);
        payload.put("max_tokens", 768);
        payload.put("stream", false);
        JsonNode response = postJson("/chat/completions", payload);
        JsonNode choices = response.get("choices");
        if (choices == null || !choices.isArray() || choices.isEmpty()) {
            throw new IllegalStateException("Chat API 返回格式异常：缺少 choices。");
        }
        JsonNode content = choices.get(0).path("message").path("content");
        if (content.isTextual()) {
            return content.asText().trim();
        }
        if (content.isArray()) {
            StringBuilder builder = new StringBuilder();
            content.forEach(part -> {
                JsonNode text = part.get("text");
                if (text != null && text.isTextual()) {
                    if (!builder.isEmpty()) {
                        builder.append('\n');
                    }
                    builder.append(text.asText());
                }
            });
            return builder.toString().trim();
        }
        String text = choices.get(0).path("text").asText("");
        if (!text.isBlank()) {
            return text.trim();
        }
        throw new IllegalStateException("Chat API 返回格式异常：未找到回答内容。");
    }

    private JsonNode postJson(String path, Map<String, Object> payload) {
        try {
            return restClient.post()
                    .uri(endpoint(path))
                    .body(payload)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception exc) {
            throw new IllegalStateException("OpenAI-compatible API 请求失败：" + exc.getMessage(), exc);
        }
    }

    private URI endpoint(String path) {
        String base = settings.baseUrl() == null ? "" : settings.baseUrl().trim().replaceAll("/+$", "");
        if (base.isBlank()) {
            throw new BadRequestException("模型 API 地址未配置。");
        }
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        if (base.endsWith("/v1")) {
            return URI.create(base + normalizedPath);
        }
        return URI.create(base + "/v1" + normalizedPath);
    }

    private String requireApiKey() {
        if (!settings.apiKeyConfigured()) {
            throw new BadRequestException("模型 API Key 未配置，请设置 SPRING_AI_OPENAI_API_KEY。");
        }
        return settings.apiKey().trim();
    }
}
