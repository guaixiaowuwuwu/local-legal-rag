package com.locallegalrag.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.locallegalrag.model.RagChunk;
import com.locallegalrag.model.RetrievedChunk;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RagChunkRepository {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public RagChunkRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void deleteByKnowledgeBase(UUID knowledgeBaseId) {
        jdbcTemplate.update("delete from rag_chunks where knowledge_base_id = ?", knowledgeBaseId);
    }

    public void deleteByDocument(UUID knowledgeBaseId, UUID documentId) {
        jdbcTemplate.update(
                "delete from rag_chunks where knowledge_base_id = ? and document_id = ?",
                knowledgeBaseId,
                documentId
        );
    }

    public void save(RagChunk chunk, List<Double> embedding) {
        jdbcTemplate.update("""
                insert into rag_chunks(id, knowledge_base_id, document_id, chunk_index, content, source,
                                       source_path, page, article, metadata, embedding)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, cast(? as jsonb), cast(? as vector))
                """,
                chunk.id(),
                chunk.knowledgeBaseId(),
                chunk.documentId(),
                chunk.chunkIndex(),
                chunk.content(),
                chunk.source(),
                chunk.sourcePath(),
                chunk.page(),
                chunk.article(),
                toJson(chunk.metadata()),
                toVectorLiteral(embedding)
        );
    }

    public List<RetrievedChunk> search(UUID knowledgeBaseId, List<Double> embedding, int topK) {
        String vector = toVectorLiteral(embedding);
        return jdbcTemplate.query("""
                select id, document_id, chunk_index, content, source, source_path, page, article, metadata,
                       (embedding <=> cast(? as vector)) as distance
                from rag_chunks
                where knowledge_base_id = ?
                order by embedding <=> cast(? as vector)
                limit ?
                """, (rs, rowNum) -> retrievedChunk(rs), vector, knowledgeBaseId, vector, topK);
    }

    private RetrievedChunk retrievedChunk(ResultSet rs) throws SQLException {
        double distance = rs.getDouble("distance");
        return new RetrievedChunk(
                rs.getObject("id", UUID.class),
                rs.getObject("document_id", UUID.class),
                rs.getInt("chunk_index"),
                rs.getString("content"),
                rs.getString("source"),
                rs.getString("source_path"),
                (Integer) rs.getObject("page"),
                rs.getString("article"),
                fromJson(rs.getString("metadata")),
                1.0d / (1.0d + Math.max(0.0d, distance))
        );
    }

    private String toJson(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata);
        } catch (Exception exc) {
            throw new IllegalArgumentException("无法序列化 chunk 元数据。", exc);
        }
    }

    private Map<String, Object> fromJson(String json) {
        try {
            return objectMapper.readValue(json == null ? "{}" : json, MAP_TYPE);
        } catch (Exception exc) {
            return Map.of();
        }
    }

    private String toVectorLiteral(List<Double> embedding) {
        if (embedding == null || embedding.isEmpty()) {
            throw new IllegalArgumentException("Embedding 不能为空。");
        }
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < embedding.size(); index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(embedding.get(index));
        }
        return builder.append(']').toString();
    }
}
