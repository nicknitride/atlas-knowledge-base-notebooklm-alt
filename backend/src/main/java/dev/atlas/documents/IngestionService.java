package dev.atlas.documents;

import dev.atlas.providers.EmbeddingProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IngestionService {
  private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

  private final KnowledgeDocumentRepository documents;
  private final IngestionJobRepository jobRepository;
  private final FileStorage storage;
  private final DocumentExtractor extractor;
  private final EmbeddingProvider embeddingProvider;
  private final JdbcTemplate jdbc;

  public IngestionService(
      KnowledgeDocumentRepository documents,
      IngestionJobRepository jobRepository,
      FileStorage storage,
      DocumentExtractor extractor,
      EmbeddingProvider embeddingProvider,
      JdbcTemplate jdbc) {
    this.documents = documents;
    this.jobRepository = jobRepository;
    this.storage = storage;
    this.extractor = extractor;
    this.embeddingProvider = embeddingProvider;
    this.jdbc = jdbc;
  }

  public void ingest(UUID documentId) {
    IngestionJob job = jobRepository.save(new IngestionJob(documentId));
    processJobAsync(job.id());
  }

  @Async
  public void processJobAsync(UUID jobId) {
    executeJob(jobId);
  }

  @EventListener(ApplicationReadyEvent.class)
  public void recoverOrphanedJobs() {
    List<IngestionJob> orphanedJobs = jobRepository.findByStatus("PROCESSING");
    if (!orphanedJobs.isEmpty()) {
      log.info("Found {} orphaned processing ingestion jobs on startup. Recovering...", orphanedJobs.size());
      for (IngestionJob job : orphanedJobs) {
        job.markPending();
        jobRepository.save(job);
      }
    }

    List<IngestionJob> pendingJobs = jobRepository.findByStatus("PENDING");
    if (!pendingJobs.isEmpty()) {
      log.info("Enqueuing {} pending ingestion jobs for execution...", pendingJobs.size());
      for (IngestionJob job : pendingJobs) {
        processJobAsync(job.id());
      }
    }
  }

  @Transactional
  public void executeJob(UUID jobId) {
    IngestionJob job = jobRepository.findById(jobId).orElse(null);
    if (job == null) return;

    KnowledgeDocument document = documents.findById(job.documentId()).orElse(null);
    if (document == null) {
      job.markFailed("Associated document no longer exists");
      jobRepository.save(job);
      return;
    }

    try {
      job.markProcessing();
      jobRepository.saveAndFlush(job);

      document.markProcessing();
      documents.saveAndFlush(document);

      List<DocumentExtractor.ExtractedSection> sections = extractor.extract(
          storage.resolve(document.storageKey()),
          document.contentType(),
          document.originalFilename()
      );

      int ordinal = 0;
      for (DocumentExtractor.ExtractedSection section : sections) {
        for (String chunkText : chunk(section.content())) {
          float[] embedding = embeddingProvider.embed(chunkText);
          String vectorStr = toVectorString(embedding);
          String sourceLocatorJson = String.format("{\"location\":\"%s\",\"filename\":\"%s\"}", 
              escapeJson(section.locator()), 
              escapeJson(document.originalFilename()));

          jdbc.update(
              "INSERT INTO document_chunks (document_id, ordinal, content, source_locator, embedding) VALUES (?, ?, ?, CAST(? AS jsonb), CAST(? AS vector))",
              document.id(), ordinal++, chunkText, sourceLocatorJson, vectorStr
          );
        }
      }

      if (ordinal == 0) {
        throw new IllegalArgumentException("No readable text was found in this document");
      }

      document.markComplete();
      documents.saveAndFlush(document);

      job.markCompleted();
      jobRepository.saveAndFlush(job);

      log.info("Document {} ingested successfully with {} chunks (Job {})", document.id(), ordinal, job.id());
    } catch (Exception exception) {
      log.error("Failed to ingest document {} (Job {})", document.id(), job.id(), exception);
      String failMsg = "The document could not be processed: " + exception.getMessage();
      document.markFailed(failMsg);
      documents.saveAndFlush(document);

      job.markFailed(failMsg);
      jobRepository.saveAndFlush(job);
    }
  }

  private String toVectorString(float[] vector) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < vector.length; i++) {
      sb.append(vector[i]);
      if (i < vector.length - 1) sb.append(",");
    }
    sb.append("]");
    return sb.toString();
  }

  private String escapeJson(String input) {
    if (input == null) return "";
    return input.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
  }

  private List<String> chunk(String text) {
    List<String> chunks = new ArrayList<>();
    String normalized = text.replaceAll("\\s+", " ").trim();
    int start = 0;
    while (start < normalized.length()) {
      int end = Math.min(normalized.length(), start + 1100);
      if (end < normalized.length()) {
        int boundary = normalized.lastIndexOf(' ', end);
        if (boundary > start + 300) end = boundary;
      }
      chunks.add(normalized.substring(start, end));
      if (end == normalized.length()) break;
      start = end - 150;
    }
    return chunks;
  }
}
