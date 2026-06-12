package com.locallegalrag.repository;

import com.locallegalrag.model.DocumentStatus;
import com.locallegalrag.model.StoredDocument;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DocumentRepository {

    private final JdbcTemplate jdbcTemplate;

    public DocumentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public StoredDocument create(UUID knowledgeBaseId, String originalFilename, String storedFilename,
            String contentType, long sizeBytes) {
        return jdbcTemplate.queryForObject("""
                insert into documents(knowledge_base_id, original_filename, stored_filename, content_type, size_bytes)
                values (?, ?, ?, ?, ?)
                returning id, knowledge_base_id, original_filename, stored_filename, content_type, size_bytes,
                          status, error_message, uploaded_at
                """, (rs, rowNum) -> document(rs), knowledgeBaseId, originalFilename, storedFilename,
                contentType, sizeBytes);
    }

    public List<StoredDocument> findByKnowledgeBase(UUID knowledgeBaseId) {
        return jdbcTemplate.query("""
                select id, knowledge_base_id, original_filename, stored_filename, content_type, size_bytes,
                       status, error_message, uploaded_at
                from documents
                where knowledge_base_id = ?
                order by uploaded_at desc
                """, (rs, rowNum) -> document(rs), knowledgeBaseId);
    }

    public Optional<StoredDocument> findById(UUID knowledgeBaseId, UUID documentId) {
        List<StoredDocument> rows = jdbcTemplate.query("""
                select id, knowledge_base_id, original_filename, stored_filename, content_type, size_bytes,
                       status, error_message, uploaded_at
                from documents
                where knowledge_base_id = ? and id = ?
                """, (rs, rowNum) -> document(rs), knowledgeBaseId, documentId);
        return rows.stream().findFirst();
    }

    public void delete(UUID knowledgeBaseId, UUID documentId) {
        jdbcTemplate.update("delete from documents where knowledge_base_id = ? and id = ?", knowledgeBaseId, documentId);
    }

    public void markStatus(UUID documentId, DocumentStatus status, String errorMessage) {
        jdbcTemplate.update("""
                update documents
                set status = ?, error_message = ?
                where id = ?
                """, status.name(), errorMessage, documentId);
    }

    private StoredDocument document(ResultSet rs) throws SQLException {
        return new StoredDocument(
                rs.getObject("id", UUID.class),
                rs.getObject("knowledge_base_id", UUID.class),
                rs.getString("original_filename"),
                rs.getString("stored_filename"),
                rs.getString("content_type"),
                rs.getLong("size_bytes"),
                DocumentStatus.valueOf(rs.getString("status")),
                rs.getString("error_message"),
                rs.getObject("uploaded_at", OffsetDateTime.class)
        );
    }
}
