package dev.atlas.documents;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.atlas.providers.EmbeddingProvider;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class IngestionWorkflowTest {
  private KnowledgeDocumentRepository documents;
  private FileStorage storage;
  private DocumentExtractor extractor;
  private EmbeddingProvider embeddingProvider;
  private JdbcTemplate jdbc;
  private IngestionService ingestionService;

  @BeforeEach
  void setUp() {
    documents = mock(KnowledgeDocumentRepository.class);
    storage = mock(FileStorage.class);
    extractor = mock(DocumentExtractor.class);
    embeddingProvider = mock(EmbeddingProvider.class);
    jdbc = mock(JdbcTemplate.class);
    ingestionService = new IngestionService(documents, storage, extractor, embeddingProvider, jdbc);
  }

  @Test
  void testIngestSuccessTransitionsToComplete() throws Exception {
    UUID docId = UUID.randomUUID();
    UUID workspaceId = UUID.randomUUID();
    KnowledgeDocument doc = new KnowledgeDocument(workspaceId, "sample.md", "text/markdown", "key123");

    when(documents.findById(docId)).thenReturn(Optional.of(doc));
    when(storage.resolve("key123")).thenReturn(Path.of("dummy/path"));
    when(extractor.extract(any(), eq("text/markdown"), eq("sample.md")))
        .thenReturn(List.of(new DocumentExtractor.ExtractedSection("Header", "Sample markdown content to ingest.")));
    when(embeddingProvider.embed(anyString())).thenReturn(new float[1536]);

    ingestionService.ingest(docId);

    assertEquals(IngestionStatus.COMPLETE, doc.ingestionStatus());
    assertNull(doc.failureReason());
    verify(jdbc, times(1)).update(anyString(), eq(doc.id()), eq(0), anyString(), anyString(), anyString());
  }

  @Test
  void testIngestEmptyTextTransitionsToFailed() throws Exception {
    UUID docId = UUID.randomUUID();
    UUID workspaceId = UUID.randomUUID();
    KnowledgeDocument doc = new KnowledgeDocument(workspaceId, "empty.txt", "text/plain", "keyEmpty");

    when(documents.findById(docId)).thenReturn(Optional.of(doc));
    when(storage.resolve("keyEmpty")).thenReturn(Path.of("dummy/path"));
    when(extractor.extract(any(), anyString(), anyString())).thenReturn(List.of());

    ingestionService.ingest(docId);

    assertEquals(IngestionStatus.FAILED, doc.ingestionStatus());
    assertNotNull(doc.failureReason());
  }
}
