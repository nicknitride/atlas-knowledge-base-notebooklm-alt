package dev.atlas.documents;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.atlas.providers.EmbeddingProvider;
import dev.atlas.support.ApiException;
import dev.atlas.support.AtlasProperties;
import dev.atlas.workspaces.WorkspaceLookup;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class RebuildConcurrencyTest {
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
  void concurrentRebuildReturnConflict() throws InterruptedException {
    UUID workspaceId = UUID.randomUUID();
    KnowledgeDocument doc = new KnowledgeDocument(workspaceId, "doc.txt", "text/plain", "key1");
    doc.markComplete("old-model", 768);

    when(documentRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId)).thenReturn(List.of(doc));
    when(jobRepository.save(any(IngestionJob.class))).thenAnswer(inv -> inv.getArgument(0));
    when(documentRepository.findById(doc.id())).thenReturn(Optional.of(doc));

    CountDownLatch startFirst = new CountDownLatch(1);
    CountDownLatch holdFirst = new CountDownLatch(1);
    
    doAnswer(inv -> {
      startFirst.countDown();
      holdFirst.await(5, TimeUnit.SECONDS);
      doc.markComplete("nomic-embed-text", 768);
      return null;
    }).when(ingestionService).executeJob(any(UUID.class));

    // Run first rebuild on a separate thread
    AtomicReference<RebuildService.RebuildResponse> responseRef = new AtomicReference<>();
    Thread t = new Thread(() -> {
      try {
        responseRef.set(rebuildService.rebuildWorkspace(workspaceId));
      } catch (Exception ignored) {}
    });
    t.start();

    // Wait until the first rebuild starts processing the job (and holds the lock)
    assertTrue(startFirst.await(2, TimeUnit.SECONDS), "First rebuild should have started");

    // Second rebuild on the main thread should fail with 409 CONFLICT
    ApiException ex = assertThrows(ApiException.class, () -> rebuildService.rebuildWorkspace(workspaceId));
    assertEquals(HttpStatus.CONFLICT, ex.status());
    assertEquals("REBUILD_IN_PROGRESS", ex.code());

    // Release the first rebuild and clean up
    holdFirst.countDown();
    t.join(2000);

    assertNotNull(responseRef.get());
    assertEquals("COMPLETED", responseRef.get().status());
  }

  @Test
  void singleRebuildCompletesNormally() {
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

    RebuildService.RebuildResponse response = rebuildService.rebuildWorkspace(workspaceId);
    assertEquals("COMPLETED", response.status());
  }
}
