package dev.atlas.documents;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.atlas.providers.EmbeddingProvider;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class IngestionRecoveryTest {
  private KnowledgeDocumentRepository documents;
  private IngestionJobRepository jobRepository;
  private FileStorage storage;
  private DocumentExtractor extractor;
  private EmbeddingProvider embeddingProvider;
  private JdbcTemplate jdbc;
  private IngestionService ingestionService;

  @BeforeEach
  void setUp() {
    documents = mock(KnowledgeDocumentRepository.class);
    jobRepository = mock(IngestionJobRepository.class);
    storage = mock(FileStorage.class);
    extractor = mock(DocumentExtractor.class);
    embeddingProvider = mock(EmbeddingProvider.class);
    jdbc = mock(JdbcTemplate.class);
    ingestionService = new IngestionService(documents, jobRepository, storage, extractor, embeddingProvider, jdbc);
  }

  @Test
  void testRecoverOrphanedJobsResetsProcessingStatusToPending() {
    IngestionJob orphanedJob = new IngestionJob(UUID.randomUUID());
    orphanedJob.markProcessing();

    when(jobRepository.findByStatus("PROCESSING")).thenReturn(List.of(orphanedJob));
    when(jobRepository.findByStatus("PENDING")).thenReturn(List.of());

    ingestionService.recoverOrphanedJobs();

    assertEquals("PENDING", orphanedJob.status());
    verify(jobRepository, times(1)).save(orphanedJob);
  }
}
