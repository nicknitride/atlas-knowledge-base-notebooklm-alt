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

class RebuildServiceTest {
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
  void testGetIndexHealthReturnsReadyWhenDocumentMatchesActiveConfig() {
    UUID workspaceId = UUID.randomUUID();
    KnowledgeDocument doc = new KnowledgeDocument(workspaceId, "doc1.pdf", "application/pdf", "key1");
    doc.markComplete("nomic-embed-text", 768);

    when(documentRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId)).thenReturn(List.of(doc));

    RebuildService.IndexHealthResponse health = rebuildService.getIndexHealth(workspaceId);

    assertEquals("READY", health.status());
    assertEquals(1, health.readyDocuments());
    assertEquals(0, health.staleDocuments());
  }

  @Test
  void testGetIndexHealthReturnsStaleWhenModelMismatched() {
    UUID workspaceId = UUID.randomUUID();
    KnowledgeDocument doc = new KnowledgeDocument(workspaceId, "doc1.pdf", "application/pdf", "key1");
    doc.markComplete("old-model", 768);

    when(documentRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId)).thenReturn(List.of(doc));

    RebuildService.IndexHealthResponse health = rebuildService.getIndexHealth(workspaceId);

    assertEquals("STALE", health.status());
    assertEquals(0, health.readyDocuments());
    assertEquals(1, health.staleDocuments());
  }

  @Test
  void testRebuildWorkspaceExecutesIngestAndStampsWorkspace() {
    UUID workspaceId = UUID.randomUUID();
    KnowledgeDocument doc = new KnowledgeDocument(workspaceId, "doc1.pdf", "application/pdf", "key1");
    doc.markComplete("old-model", 768);

    when(documentRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId)).thenReturn(List.of(doc));
    when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(documentRepository.findById(doc.id())).thenReturn(Optional.of(doc));

    doAnswer(inv -> {
      doc.markComplete("nomic-embed-text", 768);
      return null;
    }).when(ingestionService).executeJob(any());

    RebuildService.RebuildResponse response = rebuildService.rebuildWorkspace(workspaceId);

    assertEquals("COMPLETED", response.status());
    assertEquals(1, response.rebuiltCount());
    assertEquals(0, response.failedCount());
    verify(workspaceLookup).stampEmbeddingIdentity(workspaceId, "nomic-embed-text", 768);
  }

  @Test
  void rebuildAlreadyIndexedWorkspaceSucceeds() {
    UUID workspaceId = UUID.randomUUID();
    KnowledgeDocument doc = new KnowledgeDocument(workspaceId, "doc1.pdf", "application/pdf", "key1");
    doc.markComplete("old-model", 768);

    when(documentRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId)).thenReturn(List.of(doc));
    when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(documentRepository.findById(doc.id())).thenReturn(Optional.of(doc));

    doAnswer(inv -> {
      doc.markComplete("nomic-embed-text", 768);
      return null;
    }).when(ingestionService).executeJob(any());

    // First rebuild
    RebuildService.RebuildResponse response1 = rebuildService.rebuildWorkspace(workspaceId);
    assertEquals("COMPLETED", response1.status());
    assertEquals(1, response1.rebuiltCount());
    assertEquals(0, response1.failedCount());

    // Second rebuild
    RebuildService.RebuildResponse response2 = rebuildService.rebuildWorkspace(workspaceId);
    assertEquals("COMPLETED", response2.status());
    assertEquals(1, response2.rebuiltCount());
    assertEquals(0, response2.failedCount());
  }
}

