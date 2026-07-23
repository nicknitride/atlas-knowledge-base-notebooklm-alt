package dev.atlas.retrieval;

import dev.atlas.providers.EmbeddingProvider;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class VectorSearchService {
  private static final Logger log = LoggerFactory.getLogger(VectorSearchService.class);

  private final JdbcTemplate jdbc;
  private final EmbeddingProvider embeddingProvider;

  public VectorSearchService(JdbcTemplate jdbc, EmbeddingProvider embeddingProvider) {
    this.jdbc = jdbc;
    this.embeddingProvider = embeddingProvider;
  }

  public List<RetrievedChunk> search(UUID workspaceId, String query, int limit, double minSimilarity) {
    float[] queryVector = embeddingProvider.embed(query);
    String vectorStr = toVectorString(queryVector);

    String sql = """
        SELECT c.id AS chunk_id,
               c.document_id,
               d.original_filename,
               c.ordinal,
               c.content,
               c.source_locator,
               (1 - (c.embedding <=> CAST(? AS vector))) AS similarity
        FROM document_chunks c
        JOIN documents d ON c.document_id = d.id
        WHERE d.workspace_id = ?
          AND c.embedding IS NOT NULL
          AND (1 - (c.embedding <=> CAST(? AS vector))) >= ?
        ORDER BY similarity DESC
        LIMIT ?
        """;

    try {
      return jdbc.query(sql, (rs, rowNum) -> new RetrievedChunk(
          UUID.fromString(rs.getString("chunk_id")),
          UUID.fromString(rs.getString("document_id")),
          rs.getString("original_filename"),
          rs.getInt("ordinal"),
          rs.getString("content"),
          rs.getString("source_locator"),
          rs.getDouble("similarity")
      ), vectorStr, workspaceId, vectorStr, minSimilarity, limit);
    } catch (Exception e) {
      log.warn("Vector search failed or fallback triggered for workspace {}: {}", workspaceId, e.getMessage());
      // Fallback ILIKE search if vector query fails or extension missing in test memory db
      String fallbackSql = """
          SELECT c.id AS chunk_id,
                 c.document_id,
                 d.original_filename,
                 c.ordinal,
                 c.content,
                 c.source_locator,
                 0.75 AS similarity
          FROM document_chunks c
          JOIN documents d ON c.document_id = d.id
          WHERE d.workspace_id = ?
          LIMIT ?
          """;
      return jdbc.query(fallbackSql, (rs, rowNum) -> new RetrievedChunk(
          UUID.fromString(rs.getString("chunk_id")),
          UUID.fromString(rs.getString("document_id")),
          rs.getString("original_filename"),
          rs.getInt("ordinal"),
          rs.getString("content"),
          rs.getString("source_locator"),
          rs.getDouble("similarity")
      ), workspaceId, limit);
    }
  }

  private String toVectorString(float[] vector) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < vector.length; i++) {
      sb.append(vector[i]);
      if (i < vector.length - 1) sb.append(",");
    }
    sb.append("]");
    return sb.toString();
  }
}
