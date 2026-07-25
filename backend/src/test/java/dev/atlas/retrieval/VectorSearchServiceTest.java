package dev.atlas.retrieval;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.atlas.providers.EmbeddingProvider;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class VectorSearchServiceTest {
  private JdbcTemplate jdbc;
  private EmbeddingProvider embeddingProvider;
  private VectorSearchService searchService;

  @Container
  static PostgreSQLContainer postgreSQLContainer = new PostgreSQLContainer("pgvector/pgvector:pg16")
          .withDatabaseName("testdb")
          .withUsername("testname")
          .withPassword("testpass");

  @BeforeEach
  void setUp() {
    jdbc = mock(JdbcTemplate.class);
    embeddingProvider = mock(EmbeddingProvider.class);
    searchService = new VectorSearchService(jdbc, embeddingProvider);
  }
//TODO: Improve tests
  @Test
  void testSearchQueriesWorkspaceScopedVector() {
    UUID workspaceId = UUID.randomUUID();
    when(embeddingProvider.embed("query")).thenReturn(new float[1536]);
    
    RetrievedChunk expectedChunk = new RetrievedChunk(
        UUID.randomUUID(), UUID.randomUUID(), "test.pdf", 1, "Extracted text content", "{}", 0.88
    );
    when(jdbc.query(anyString(), any(RowMapper.class), anyString(), eq(workspaceId), anyString(), eq(0.2), eq(5)))
        .thenReturn(List.of(expectedChunk));

    List<RetrievedChunk> results = searchService.search(workspaceId, "query", 5, 0.2);

    assertNotNull(results);
    assertEquals(1, results.size());
    assertEquals("test.pdf", results.get(0).documentFilename());
  }
}
