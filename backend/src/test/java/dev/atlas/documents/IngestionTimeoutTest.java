package dev.atlas.documents;

import static org.mockito.Mockito.*;

import dev.atlas.providers.EmbeddingProvider;
import dev.atlas.support.AtlasProperties;
import dev.atlas.workspaces.WorkspaceLookup;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class IngestionTimeoutTest {
  @Test
  void failTimedOutJobsMarksDocumentFailed() {
    KnowledgeDocumentRepository documents = mock(KnowledgeDocumentRepository.class);
    IngestionJobRepository jobRepository = mock(IngestionJobRepository.class);
    AtlasProperties properties = new AtlasProperties();
    properties.getIngestion().setProcessingTimeout(Duration.ofMinutes(10));

    IngestionService service = new IngestionService(
        documents,
        jobRepository,
        mock(FileStorage.class),
        mock(DocumentExtractor.class),
        mock(EmbeddingProvider.class),
        mock(JdbcTemplate.class),
        properties,
        mock(WorkspaceLookup.class));

    UUID documentId = UUID.randomUUID();
    IngestionJob job = new IngestionJob(documentId);
    job.markProcessing();
    // Force started_at into the past via reflection-free approach: mark then overwrite using failed path
    // by constructing a job and using package access - use markProcessing then mutate via failing check:
    try {
      var field = IngestionJob.class.getDeclaredField("startedAt");
      field.setAccessible(true);
      field.set(job, java.time.Instant.now().minus(Duration.ofMinutes(11)));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    KnowledgeDocument doc = new KnowledgeDocument(UUID.randomUUID(), "a.md", "text/markdown", "k");
    when(jobRepository.findByStatus("PROCESSING")).thenReturn(List.of(job));
    when(documents.findById(documentId)).thenReturn(Optional.of(doc));
    when(documents.existsById(any())).thenReturn(true);
    when(jobRepository.existsById(any())).thenReturn(true);
    when(documents.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    when(jobRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.failTimedOutJobs();

    verify(documents).saveAndFlush(argThat(d -> d.ingestionStatus() == IngestionStatus.FAILED));
  }
}
