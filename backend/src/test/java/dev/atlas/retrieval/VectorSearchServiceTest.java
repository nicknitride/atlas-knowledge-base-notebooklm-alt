package dev.atlas.retrieval;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.atlas.providers.EmbeddingProvider;
import dev.atlas.support.ApiException;
import dev.atlas.support.AtlasProperties;
import dev.atlas.workspaces.WorkspaceLookup;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class VectorSearchServiceTest {
  private JdbcTemplate jdbc;
  private EmbeddingProvider embeddingProvider;
  private WorkspaceLookup workspaces;
  private AtlasProperties properties;
  private VectorSearchService searchService;

  @BeforeEach
  void setUp() {
    jdbc = mock(JdbcTemplate.class);
    embeddingProvider = mock(EmbeddingProvider.class);
    workspaces = mock(WorkspaceLookup.class);
    properties = new AtlasProperties();
    searchService = new VectorSearchService(jdbc, embeddingProvider, workspaces, properties);
    when(embeddingProvider.embeddingModelName()).thenReturn("nomic-embed-text");
    when(embeddingProvider.embeddingDimensions()).thenReturn(768);
    when(embeddingProvider.embed("query")).thenReturn(new float[768]);
  }

  @Test
  void testSearchQueriesWorkspaceScopedCompleteDocuments() {
    UUID workspaceId = UUID.randomUUID();
    RetrievedChunk expectedChunk = new RetrievedChunk(
        UUID.randomUUID(), UUID.randomUUID(), "test.pdf", 1, "Extracted text content", "{}", 0.88);
    when(jdbc.query(anyString(), any(RowMapper.class), any(), eq(workspaceId), any(), eq(0.2), eq(5)))
        .thenReturn(List.of(expectedChunk));

    List<RetrievedChunk> results = searchService.search(workspaceId, "query", 5, 0.2);

    assertEquals(1, results.size());
    assertEquals("test.pdf", results.get(0).documentFilename());
    verify(jdbc).query(argThat(sql -> sql.contains("ingestion_status = 'COMPLETE'")), any(RowMapper.class), any(), any(), any(), any(), any());
  }

  @Test
  void doesNotFabricateSimilarityOnJdbcFailure() {
    UUID workspaceId = UUID.randomUUID();
    when(jdbc.query(anyString(), any(RowMapper.class), any(), any(), any(), any(), any()))
        .thenThrow(new RuntimeException("vector type missing"));

    ApiException ex = assertThrows(ApiException.class, () -> searchService.search(workspaceId, "query", 5, 0.2));
    assertEquals("RETRIEVAL_UNAVAILABLE", ex.code());
  }
}
