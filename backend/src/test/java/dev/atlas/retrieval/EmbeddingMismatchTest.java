package dev.atlas.retrieval;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.atlas.providers.EmbeddingProvider;
import dev.atlas.support.ApiException;
import dev.atlas.support.AtlasProperties;
import dev.atlas.workspaces.WorkspaceLookup;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class EmbeddingMismatchTest {
  @Test
  void searchFailsWhenWorkspaceIdentityMismatchesConfig() {
    WorkspaceLookup workspaces = mock(WorkspaceLookup.class);
    EmbeddingProvider embeddingProvider = mock(EmbeddingProvider.class);
    when(embeddingProvider.embeddingModelName()).thenReturn("nomic-embed-text");
    when(embeddingProvider.embeddingDimensions()).thenReturn(768);
    doThrow(new ApiException(
            org.springframework.http.HttpStatus.CONFLICT,
            "EMBEDDING_CONFIG_MISMATCH",
            "mismatch"))
        .when(workspaces)
        .requireCompatibleEmbeddingConfig(any(), eq("nomic-embed-text"), eq(768));

    VectorSearchService service = new VectorSearchService(
        mock(JdbcTemplate.class), embeddingProvider, workspaces, new AtlasProperties());

    ApiException ex = assertThrows(
        ApiException.class, () -> service.search(UUID.randomUUID(), "query", 5, 0.1));
    assertEquals("EMBEDDING_CONFIG_MISMATCH", ex.code());
  }
}
