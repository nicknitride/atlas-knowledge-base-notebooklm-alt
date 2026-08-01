package dev.atlas.documents;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.atlas.providers.EmbeddingProvider;
import dev.atlas.support.AtlasProperties;
import dev.atlas.workspaces.WorkspaceLookup;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class IngestionWorkflowTest {
  private KnowledgeDocumentRepository documents;
  private IngestionJobRepository jobRepository;
  private FileStorage storage;
  private DocumentExtractor extractor;
  private EmbeddingProvider embeddingProvider;
  private JdbcTemplate jdbc;
  private WorkspaceLookup workspaces;
  private AtlasProperties properties;
  private IngestionService ingestionService;

  @BeforeEach
  void setUp() {
    documents = mock(KnowledgeDocumentRepository.class);
    jobRepository = mock(IngestionJobRepository.class);
    storage = mock(FileStorage.class);
    extractor = mock(DocumentExtractor.class);
    embeddingProvider = mock(EmbeddingProvider.class);
    jdbc = mock(JdbcTemplate.class);
    workspaces = mock(WorkspaceLookup.class);
    properties = new AtlasProperties();
    ingestionService = new IngestionService(
        documents, jobRepository, storage, extractor, embeddingProvider, jdbc, properties, workspaces);

    when(jobRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(jobRepository.findById(any())).thenAnswer(invocation -> {
      IngestionJob job = new IngestionJob(UUID.randomUUID());
      return Optional.of(job);
    });
    when(jobRepository.existsById(any())).thenReturn(true);
    when(documents.existsById(any())).thenReturn(true);
    when(embeddingProvider.embeddingModelName()).thenReturn("nomic-embed-text");
    when(embeddingProvider.embeddingDimensions()).thenReturn(768);
  }

  @Test
  void testIngestSuccessTransitionsToComplete() throws Exception {
    UUID workspaceId = UUID.randomUUID();
    KnowledgeDocument doc = new KnowledgeDocument(workspaceId, "sample.md", "text/markdown", "key123");

    when(documents.findById(any())).thenReturn(Optional.of(doc));
    when(storage.resolve("key123")).thenReturn(Path.of("dummy/path"));
    when(extractor.extract(any(), eq("text/markdown"), eq("sample.md")))
        .thenReturn(List.of(new DocumentExtractor.ExtractedSection("Header", "Sample markdown content to ingest.")));
    when(embeddingProvider.embed(anyString())).thenReturn(new float[768]);

    ingestionService.executeJob(UUID.randomUUID());

    assertEquals(IngestionStatus.COMPLETE, doc.ingestionStatus());
    assertNull(doc.failureReason());
    verify(workspaces).stampEmbeddingIdentity(workspaceId, "nomic-embed-text", 768);
    verify(jdbc, times(1)).update(anyString(), eq(doc.id()), eq(0), anyString(), anyString(), anyString());
  }

  @Test
  void testIngestEmptyTextTransitionsToFailed() throws Exception {
    UUID workspaceId = UUID.randomUUID();
    KnowledgeDocument doc = new KnowledgeDocument(workspaceId, "empty.txt", "text/plain", "keyEmpty");

    when(documents.findById(any())).thenReturn(Optional.of(doc));
    when(storage.resolve("keyEmpty")).thenReturn(Path.of("dummy/path"));
    when(extractor.extract(any(), anyString(), anyString())).thenReturn(List.of());

    ingestionService.executeJob(UUID.randomUUID());

    assertEquals(IngestionStatus.FAILED, doc.ingestionStatus());
    assertNotNull(doc.failureReason());
    verify(jdbc).update(eq("DELETE FROM document_chunks WHERE document_id = ?"), eq(doc.id()));
  }
}
