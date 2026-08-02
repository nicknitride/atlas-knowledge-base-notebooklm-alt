package dev.atlas.documents;

import dev.atlas.providers.EmbeddingProvider;
import dev.atlas.support.ApiException;
import dev.atlas.support.AtlasProperties;
import dev.atlas.workspaces.WorkspaceLookup;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
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
  private final AtlasProperties properties;
  private final WorkspaceLookup workspaces;

  public IngestionService(
      KnowledgeDocumentRepository documents,
      IngestionJobRepository jobRepository,
      FileStorage storage,
      DocumentExtractor extractor,
      EmbeddingProvider embeddingProvider,
      JdbcTemplate jdbc,
      AtlasProperties properties,
      WorkspaceLookup workspaces) {
    this.documents = documents;
    this.jobRepository = jobRepository;
    this.storage = storage;
    this.extractor = extractor;
    this.embeddingProvider = embeddingProvider;
    this.jdbc = jdbc;
    this.properties = properties;
    this.workspaces = workspaces;
  }

  public void ingest(UUID documentId) {
    IngestionJob job = jobRepository.save(new IngestionJob(documentId));
    processJobAsync(job.id());
  }

  public void cancelJobsForDocument(UUID documentId) {
    List<IngestionJob> jobs = jobRepository.findByDocumentId(documentId);
    for (IngestionJob job : jobs) {
      if ("PENDING".equals(job.status()) || "PROCESSING".equals(job.status())) {
        job.markFailed("Cancelled because the document was deleted");
        jobRepository.save(job);
      }
    }
  }

  @Async
  public void processJobAsync(UUID jobId) {
    executeJob(jobId);
  }

  @EventListener(ApplicationReadyEvent.class)
  public void recoverOrphanedJobs() {
    failTimedOutJobs();
    List<IngestionJob> orphanedJobs = jobRepository.findByStatus("PROCESSING");
    if (!orphanedJobs.isEmpty()) {
      log.info("Found {} orphaned processing ingestion jobs on startup. Recovering...", orphanedJobs.size());
      for (IngestionJob job : orphanedJobs) {
        if (isTimedOut(job)) {
          failJobAndDocument(job, "Ingestion timed out while processing");
        } else {
          job.markPending();
          jobRepository.save(job);
        }
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

  @Scheduled(fixedDelayString = "${atlas.ingestion.timeout-check-ms:60000}")
  public void failTimedOutJobs() {
    Duration timeout = properties.getIngestion().getProcessingTimeout();
    List<IngestionJob> processing = jobRepository.findByStatus("PROCESSING");
    for (IngestionJob job : processing) {
      if (isTimedOut(job, timeout)) {
        failJobAndDocument(job, "Ingestion timed out after " + timeout.toMinutes() + " minutes");
      }
    }
  }

  @Transactional
  public void executeJob(UUID jobId) {
    IngestionJob job = jobRepository.findById(jobId).orElse(null);
    if (job == null) {
      return;
    }

    KnowledgeDocument document = documents.findById(job.documentId()).orElse(null);
    if (document == null) {
      job.markFailed("Associated document no longer exists");
      try {
        jobRepository.save(job);
      } catch (Exception ignored) {
        // Job row may already be cascade-deleted with the document
      }
      return;
    }

    try {
      job.markProcessing();
      jobRepository.saveAndFlush(job);

      document.markProcessing();
      documents.saveAndFlush(document);

      String embeddingModel = embeddingProvider.embeddingModelName();
      int embeddingDimensions = embeddingProvider.embeddingDimensions();
      workspaces.requireCompatibleEmbeddingConfig(document.workspaceId(), embeddingModel, embeddingDimensions);

      List<DocumentExtractor.ExtractedSection> sections = extractor.extract(
          storage.resolve(document.storageKey()),
          document.contentType(),
          document.originalFilename());

      if (!documents.existsById(document.id())) {
        log.info("Document {} deleted during ingest; aborting job {}", document.id(), job.id());
        return;
      }

      jdbc.update("DELETE FROM document_chunks WHERE document_id = ?", document.id());

      int ordinal = 0;
      for (DocumentExtractor.ExtractedSection section : sections) {
        for (String chunkText : chunk(section.content())) {
          if (!documents.existsById(document.id())) {
            log.info("Document {} deleted mid-chunking; aborting job {}", document.id(), job.id());
            return;
          }
          float[] embedding = embeddingProvider.embed(chunkText);
          if (embedding.length != embeddingDimensions) {
            throw new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "PROVIDER_MISCONFIGURED",
                "Embedding provider returned " + embedding.length + " dimensions but config expects "
                    + embeddingDimensions);
          }
          String vectorStr = toVectorString(embedding);
          String sourceLocatorJson = String.format(
              "{\"location\":\"%s\",\"filename\":\"%s\"}",
              escapeJson(section.locator()),
              escapeJson(document.originalFilename()));

          jdbc.update(
              "INSERT INTO document_chunks (document_id, ordinal, content, source_locator, embedding) VALUES (?, ?, ?, CAST(? AS jsonb), CAST(? AS vector))",
              document.id(),
              ordinal++,
              chunkText,
              sourceLocatorJson,
              vectorStr);
        }
      }

      if (ordinal == 0) {
        throw new IllegalArgumentException("No readable text was found in this document");
      }

      if (!documents.existsById(document.id())) {
        log.info("Document {} deleted before complete; aborting job {}", document.id(), job.id());
        return;
      }

      document.markComplete(embeddingModel, embeddingDimensions);
      documents.saveAndFlush(document);
      workspaces.stampEmbeddingIdentity(document.workspaceId(), embeddingModel, embeddingDimensions);

      job.markCompleted();
      jobRepository.saveAndFlush(job);

      log.info("Document {} ingested successfully with {} chunks (Job {})", document.id(), ordinal, job.id());
    } catch (ApiException apiException) {
      failDocument(document, job, apiException.getMessage());
    } catch (Exception exception) {
      log.error("Failed to ingest document {} (Job {})", document.id(), job.id(), exception);
      String failMsg = "The document could not be processed: " + safeReason(exception.getMessage());
      failDocument(document, job, failMsg);
    }
  }

  private void failDocument(KnowledgeDocument document, IngestionJob job, String reason) {
    try {
      jdbc.update("DELETE FROM document_chunks WHERE document_id = ?", document.id());
    } catch (Exception e) {
      log.warn("Failed to clean partial chunks for document {}: {}", document.id(), e.getMessage());
    }
    if (documents.existsById(document.id())) {
      document.markFailed(reason);
      documents.saveAndFlush(document);
    }
    if (jobRepository.existsById(job.id())) {
      job.markFailed(reason);
      jobRepository.saveAndFlush(job);
    }
  }

  private void failJobAndDocument(IngestionJob job, String reason) {
    KnowledgeDocument document = documents.findById(job.documentId()).orElse(null);
    if (document != null) {
      failDocument(document, job, reason);
    } else if (jobRepository.existsById(job.id())) {
      job.markFailed(reason);
      jobRepository.save(job);
    }
  }

  private boolean isTimedOut(IngestionJob job) {
    return isTimedOut(job, properties.getIngestion().getProcessingTimeout());
  }

  private boolean isTimedOut(IngestionJob job, Duration timeout) {
    Instant started = job.startedAt() != null ? job.startedAt() : job.updatedAt();
    if (started == null) {
      return false;
    }
    return started.plus(timeout).isBefore(Instant.now());
  }

  private static String safeReason(String message) {
    if (message == null || message.isBlank()) {
      return "Unknown processing error";
    }
    return message.length() > 500 ? message.substring(0, 500) : message;
  }

  private String toVectorString(float[] vector) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < vector.length; i++) {
      sb.append(vector[i]);
      if (i < vector.length - 1) {
        sb.append(",");
      }
    }
    sb.append("]");
    return sb.toString();
  }

  private String escapeJson(String input) {
    if (input == null) {
      return "";
    }
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
        if (boundary > start + 300) {
          end = boundary;
        }
      }
      chunks.add(normalized.substring(start, end));
      if (end == normalized.length()) {
        break;
      }
      start = end - 150;
    }
    return chunks;
  }
}
