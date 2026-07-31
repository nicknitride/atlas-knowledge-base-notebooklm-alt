package dev.atlas.retrieval;

import dev.atlas.providers.EmbeddingProvider;
import dev.atlas.support.ApiException;
import dev.atlas.support.AtlasProperties;
import dev.atlas.workspaces.WorkspaceLookup;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class VectorSearchService {
  private static final Logger log = LoggerFactory.getLogger(VectorSearchService.class);

  private final JdbcTemplate jdbc;
  private final EmbeddingProvider embeddingProvider;
  private final WorkspaceLookup workspaces;
  private final AtlasProperties properties;

  public VectorSearchService(
      JdbcTemplate jdbc,
      EmbeddingProvider embeddingProvider,
      WorkspaceLookup workspaces,
      AtlasProperties properties) {
    this.jdbc = jdbc;
    this.embeddingProvider = embeddingProvider;
    this.workspaces = workspaces;
    this.properties = properties;
  }

  public List<RetrievedChunk> search(UUID workspaceId, String query, int limit, double minSimilarity) {
    String model = embeddingProvider.embeddingModelName();
    int dimensions = embeddingProvider.embeddingDimensions();
    workspaces.requireCompatibleEmbeddingConfig(workspaceId, model, dimensions);

    WorkspaceLookup.EmbeddingIdentity identity = workspaces.embeddingIdentity(workspaceId);
    if (identity != null
        && (!identity.model().equals(properties.getProvider().getOllama().getEmbeddingModel())
            || identity.dimensions() != properties.getProvider().getOllama().getEmbeddingDimensions())) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "EMBEDDING_CONFIG_MISMATCH",
          "Configured embedding model does not match indexed vectors. Re-index or restore the prior model.");
    }

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
          AND d.ingestion_status = 'COMPLETE'
          AND c.embedding IS NOT NULL
          AND (1 - (c.embedding <=> CAST(? AS vector))) >= ?
        ORDER BY similarity DESC
        LIMIT ?
        """;

    try {
      return jdbc.query(
          sql,
          (rs, rowNum) -> new RetrievedChunk(
              UUID.fromString(rs.getString("chunk_id")),
              UUID.fromString(rs.getString("document_id")),
              rs.getString("original_filename"),
              rs.getInt("ordinal"),
              rs.getString("content"),
              rs.getString("source_locator"),
              rs.getDouble("similarity")),
          vectorStr,
          workspaceId,
          vectorStr,
          minSimilarity,
          limit);
    } catch (ApiException ex) {
      throw ex;
    } catch (Exception e) {
      log.warn("Vector search failed for workspace {}: {}", workspaceId, e.getMessage());
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "RETRIEVAL_UNAVAILABLE",
          "Search is unavailable. Check the vector database and embedding configuration.");
    }
  }

  private String toVectorString(float[] vector) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < vector.length; i++) {
      sb.append(vector[i]);
      if (i < vector.length - 1) {
        sb.append(",");
      }
    }
    sb.append("]");
    return sb.toString();
  }
}
