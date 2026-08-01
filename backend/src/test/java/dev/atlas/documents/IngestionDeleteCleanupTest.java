package dev.atlas.documents;

import static org.mockito.Mockito.*;

import dev.atlas.providers.EmbeddingProvider;
import dev.atlas.support.AtlasProperties;
import dev.atlas.workspaces.WorkspaceLookup;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class IngestionDeleteCleanupTest {
  @Test
  void executeJobAbortsWhenDocumentMissing() {
    KnowledgeDocumentRepository documents = mock(KnowledgeDocumentRepository.class);
    IngestionJobRepository jobRepository = mock(IngestionJobRepository.class);
    IngestionService service = new IngestionService(
        documents,
        jobRepository,
        mock(FileStorage.class),
        mock(DocumentExtractor.class),
        mock(EmbeddingProvider.class),
        mock(JdbcTemplate.class),
        new AtlasProperties(),
        mock(WorkspaceLookup.class));

    UUID jobId = UUID.randomUUID();
    IngestionJob job = new IngestionJob(UUID.randomUUID());
    when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
    when(documents.findById(job.documentId())).thenReturn(Optional.empty());
    when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.executeJob(jobId);

    verify(jobRepository).save(argThat(j -> "FAILED".equals(j.status())));
    verify(documents, never()).saveAndFlush(any());
  }

  @Test
  void cancelJobsMarksInFlightFailed() {
    IngestionJobRepository jobRepository = mock(IngestionJobRepository.class);
    IngestionService service = new IngestionService(
        mock(KnowledgeDocumentRepository.class),
        jobRepository,
        mock(FileStorage.class),
        mock(DocumentExtractor.class),
        mock(EmbeddingProvider.class),
        mock(JdbcTemplate.class),
        new AtlasProperties(),
        mock(WorkspaceLookup.class));

    UUID documentId = UUID.randomUUID();
    IngestionJob job = new IngestionJob(documentId);
    job.markProcessing();
    when(jobRepository.findByDocumentId(documentId)).thenReturn(List.of(job));
    when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.cancelJobsForDocument(documentId);

    verify(jobRepository).save(argThat(j -> "FAILED".equals(j.status()) && j.errorMessage().contains("deleted")));
  }
}
