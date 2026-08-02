package dev.atlas.documents;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.atlas.providers.EmbeddingProvider;
import dev.atlas.support.AtlasProperties;
import dev.atlas.workspaces.WorkspaceLookup;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RebuildIdempotencyTest {
  private KnowledgeDocumentRepository documentRepository;
  private IngestionJobRepository jobRepository;
  private IngestionService ingestionService;
  private EmbeddingProvider embeddingProvider;
  private WorkspaceLookup workspaceLookup;
  private AtlasProperties properties;
  private RebuildService rebuildService;

  @BeforeEach
  void setUp() {
    documentRepository = mock(KnowledgeDocumentRepository.class);
    jobRepository = mock(IngestionJobRepository.class);
    ingestionService = mock(IngestionService.class);
    embeddingProvider = mock(EmbeddingProvider.class);
    workspaceLookup = mock(WorkspaceLookup.class);
    properties = new AtlasProperties();

    when(embeddingProvider.embeddingModelName()).thenReturn("nomic-embed-text");
    when(embeddingProvider.embeddingDimensions()).thenReturn(768);

    rebuildService = new RebuildService(
        documentRepository, jobRepository, ingestionService, embeddingProvider, workspaceLookup, properties);
  }

  @Test
  void threeConsecutiveRebuildsAllSucceed() {
    UUID workspaceId = UUID.randomUUID();
    KnowledgeDocument doc = new KnowledgeDocument(workspaceId, "doc.txt", "text/plain", "key1");
    doc.markComplete("old-model", 768);

    when(documentRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId)).thenReturn(List.of(doc));
    when(jobRepository.save(any(IngestionJob.class))).thenAnswer(inv -> inv.getArgument(0));
    when(documentRepository.findById(doc.id())).thenReturn(Optional.of(doc));

    doAnswer(inv -> {
      doc.markComplete("nomic-embed-text", 768);
      return null;
    }).when(ingestionService).executeJob(any(UUID.class));

    // Execute rebuild three times in a row
    for (int i = 0; i < 3; i++) {
      RebuildService.RebuildResponse response = rebuildService.rebuildWorkspace(workspaceId);
      assertEquals("COMPLETED", response.status(), "Invocation " + i + " should succeed");
      assertEquals(1, response.rebuiltCount());
      assertEquals(0, response.failedCount());
    }
  }

  @Test
  void rebuildAfterPartialFailureSucceeds() {
    UUID workspaceId = UUID.randomUUID();
    KnowledgeDocument doc1 = new KnowledgeDocument(workspaceId, "doc1.txt", "text/plain", "key1");
    KnowledgeDocument doc2 = new KnowledgeDocument(workspaceId, "doc2.txt", "text/plain", "key2");

    when(documentRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId)).thenReturn(List.of(doc1, doc2));
    when(jobRepository.save(any(IngestionJob.class))).thenAnswer(inv -> inv.getArgument(0));
    when(documentRepository.findById(doc1.id())).thenReturn(Optional.of(doc1));
    when(documentRepository.findById(doc2.id())).thenReturn(Optional.of(doc2));

    // First rebuild: doc1 fails, doc2 succeeds
    doAnswer(inv -> {
      doc1.markFailed("Failed intentionally");
      return null;
    }).doAnswer(inv -> {
      doc2.markComplete("nomic-embed-text", 768);
      return null;
    }).when(ingestionService).executeJob(any(UUID.class));

    RebuildService.RebuildResponse response1 = rebuildService.rebuildWorkspace(workspaceId);
    assertEquals("PARTIAL_FAILURE", response1.status());
    assertEquals(1, response1.rebuiltCount());
    assertEquals(1, response1.failedCount());

    // Reset stubbing for second rebuild: both succeed
    reset(ingestionService);
    doAnswer(inv -> {
      doc1.markComplete("nomic-embed-text", 768);
      return null;
    }).doAnswer(inv -> {
      doc2.markComplete("nomic-embed-text", 768);
      return null;
    }).when(ingestionService).executeJob(any(UUID.class));

    // Second rebuild should complete fully without being blocked or rolling back
    RebuildService.RebuildResponse response2 = rebuildService.rebuildWorkspace(workspaceId);
    assertEquals("COMPLETED", response2.status());
    assertEquals(2, response2.rebuiltCount());
    assertEquals(0, response2.failedCount());
  }
}
