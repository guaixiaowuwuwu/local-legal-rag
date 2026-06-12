package com.locallegalrag.repository;

import com.locallegalrag.model.IngestJobStatus;
import com.locallegalrag.model.KnowledgeBase;
import com.locallegalrag.model.KnowledgeBaseSummary;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class KnowledgeBaseRepository {

    private final JdbcTemplate jdbcTemplate;

    public KnowledgeBaseRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<KnowledgeBaseSummary> findAllSummaries() {
        return jdbcTemplate.query("""
                select kb.id, kb.name, kb.description, kb.created_at, kb.updated_at,
                       count(distinct d.id) as document_count,
                       count(distinct c.id) as chunk_count,
                       (
                           select ij.status
                           from ingest_jobs ij
                           where ij.knowledge_base_id = kb.id
                           order by ij.created_at desc
                           limit 1
                       ) as latest_job_status
                from knowledge_bases kb
                left join documents d on d.knowledge_base_id = kb.id
                left join rag_chunks c on c.knowledge_base_id = kb.id
                group by kb.id
                order by kb.created_at desc
                """, (rs, rowNum) -> summary(rs));
    }

    public Optional<KnowledgeBaseSummary> findSummary(UUID id) {
        List<KnowledgeBaseSummary> rows = jdbcTemplate.query("""
                select kb.id, kb.name, kb.description, kb.created_at, kb.updated_at,
                       count(distinct d.id) as document_count,
                       count(distinct c.id) as chunk_count,
                       (
                           select ij.status
                           from ingest_jobs ij
                           where ij.knowledge_base_id = kb.id
                           order by ij.created_at desc
                           limit 1
                       ) as latest_job_status
                from knowledge_bases kb
                left join documents d on d.knowledge_base_id = kb.id
                left join rag_chunks c on c.knowledge_base_id = kb.id
                where kb.id = ?
                group by kb.id
                """, (rs, rowNum) -> summary(rs), id);
        return rows.stream().findFirst();
    }

    public Optional<KnowledgeBase> findById(UUID id) {
        List<KnowledgeBase> rows = jdbcTemplate.query("""
                select id, name, description, created_at, updated_at
                from knowledge_bases
                where id = ?
                """, (rs, rowNum) -> knowledgeBase(rs), id);
        return rows.stream().findFirst();
    }

    public KnowledgeBase create(String name, String description) {
        return jdbcTemplate.queryForObject("""
                insert into knowledge_bases(name, description)
                values (?, ?)
                returning id, name, description, created_at, updated_at
                """, (rs, rowNum) -> knowledgeBase(rs), name.trim(), description == null ? "" : description.trim());
    }

    private KnowledgeBase knowledgeBase(ResultSet rs) throws SQLException {
        return new KnowledgeBase(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("description"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)
        );
    }

    private KnowledgeBaseSummary summary(ResultSet rs) throws SQLException {
        String latest = rs.getString("latest_job_status");
        return new KnowledgeBaseSummary(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("description"),
                rs.getLong("document_count"),
                rs.getLong("chunk_count"),
                latest == null ? null : IngestJobStatus.valueOf(latest),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)
        );
    }
}
