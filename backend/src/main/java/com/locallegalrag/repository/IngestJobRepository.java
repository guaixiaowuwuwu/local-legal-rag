package com.locallegalrag.repository;

import com.locallegalrag.model.IngestJob;
import com.locallegalrag.model.IngestJobStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class IngestJobRepository {

    private final JdbcTemplate jdbcTemplate;

    public IngestJobRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public IngestJob create(UUID knowledgeBaseId, boolean resetIndex) {
        return jdbcTemplate.queryForObject("""
                insert into ingest_jobs(knowledge_base_id, status, reset_index)
                values (?, ?, ?)
                returning id, knowledge_base_id, status, reset_index, total_documents, processed_documents,
                          total_chunks, error_message, created_at, started_at, finished_at
                """, (rs, rowNum) -> job(rs), knowledgeBaseId, IngestJobStatus.PENDING.name(), resetIndex);
    }

    public Optional<IngestJob> findById(UUID id) {
        List<IngestJob> rows = jdbcTemplate.query("""
                select id, knowledge_base_id, status, reset_index, total_documents, processed_documents,
                       total_chunks, error_message, created_at, started_at, finished_at
                from ingest_jobs
                where id = ?
                """, (rs, rowNum) -> job(rs), id);
        return rows.stream().findFirst();
    }

    public void start(UUID id, int totalDocuments) {
        jdbcTemplate.update("""
                update ingest_jobs
                set status = ?, started_at = now(), total_documents = ?, processed_documents = 0,
                    total_chunks = 0, error_message = null
                where id = ?
                """, IngestJobStatus.RUNNING.name(), totalDocuments, id);
    }

    public void progress(UUID id, int processedDocuments, int totalChunks) {
        jdbcTemplate.update("""
                update ingest_jobs
                set processed_documents = ?, total_chunks = ?
                where id = ?
                """, processedDocuments, totalChunks, id);
    }

    public void finish(UUID id, int processedDocuments, int totalChunks) {
        jdbcTemplate.update("""
                update ingest_jobs
                set status = ?, processed_documents = ?, total_chunks = ?, finished_at = now()
                where id = ?
                """, IngestJobStatus.SUCCEEDED.name(), processedDocuments, totalChunks, id);
    }

    public void fail(UUID id, String errorMessage) {
        jdbcTemplate.update("""
                update ingest_jobs
                set status = ?, error_message = ?, finished_at = now()
                where id = ?
                """, IngestJobStatus.FAILED.name(), errorMessage, id);
    }

    private IngestJob job(ResultSet rs) throws SQLException {
        return new IngestJob(
                rs.getObject("id", UUID.class),
                rs.getObject("knowledge_base_id", UUID.class),
                IngestJobStatus.valueOf(rs.getString("status")),
                rs.getBoolean("reset_index"),
                rs.getInt("total_documents"),
                rs.getInt("processed_documents"),
                rs.getInt("total_chunks"),
                rs.getString("error_message"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("started_at", OffsetDateTime.class),
                rs.getObject("finished_at", OffsetDateTime.class)
        );
    }
}
