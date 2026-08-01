package dev.atlas.retrieval;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import dev.atlas.providers.EmbeddingProvider;
import dev.atlas.support.AtlasProperties;
import dev.atlas.workspaces.WorkspaceLookup;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class CompleteOnlyRetrievalTest {
  @Test
  void sqlRequiresCompleteIngestionStatus() {
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    EmbeddingProvider embeddingProvider = mock(EmbeddingProvider.class);
    when(embeddingProvider.embeddingModelName()).thenReturn("nomic-embed-text");
    when(embeddingProvider.embeddingDimensions()).thenReturn(768);
    when(embeddingProvider.embed(anyString())).thenReturn(new float[768]);
    when(jdbc.query(anyString(), any(RowMapper.class), any(), any(), any(), any(), any())).thenReturn(List.of());

    VectorSearchService service = new VectorSearchService(
        jdbc, embeddingProvider, mock(WorkspaceLookup.class), new AtlasProperties());
    service.search(UUID.randomUUID(), "q", 5, 0.1);

    verify(jdbc).query(
        argThat(sql -> sql.contains("d.ingestion_status = 'COMPLETE'")),
        any(RowMapper.class),
        any(),
        any(),
        any(),
        any(),
        any());
  }
}
