package dev.atlas.documents;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.atlas.providers.EmbeddingProvider;
import dev.atlas.support.AtlasProperties;
import dev.atlas.workspaces.WorkspaceLookup;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class IngestionRecoveryTest {
  private IngestionJobRepository jobRepository;
  private IngestionService ingestionService;

  @BeforeEach
  void setUp() {
    jobRepository = mock(IngestionJobRepository.class);
    ingestionService = new IngestionService(
        mock(KnowledgeDocumentRepository.class),
        jobRepository,
        mock(FileStorage.class),
        mock(DocumentExtractor.class),
        mock(EmbeddingProvider.class),
        mock(JdbcTemplate.class),
        new AtlasProperties(),
        mock(WorkspaceLookup.class));
  }

  @Test
  void testRecoverOrphanedJobsResetsProcessingStatusToPending() {
    IngestionJob orphanedJob = new IngestionJob(UUID.randomUUID());
    orphanedJob.markProcessing();

    when(jobRepository.findByStatus("PROCESSING")).thenReturn(List.of(orphanedJob));
    when(jobRepository.findByStatus("PENDING")).thenReturn(List.of());

    ingestionService.recoverOrphanedJobs();

    assertEquals("PENDING", orphanedJob.status());
    verify(jobRepository, atLeastOnce()).save(orphanedJob);
  }
}
