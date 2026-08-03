package dev.atlas.documents;

import dev.atlas.providers.EmbeddingProvider;
import dev.atlas.support.ApiException;
import dev.atlas.support.AtlasProperties;
import dev.atlas.workspaces.WorkspaceLookup;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RebuildService {
  private static final Logger log = LoggerFactory.getLogger(RebuildService.class);

  private final KnowledgeDocumentRepository documentRepository;
  private final IngestionJobRepository jobRepository;
  private final IngestionService ingestionService;
  private final EmbeddingProvider embeddingProvider;
  private final WorkspaceLookup workspaceLookup;
  private final AtlasProperties properties;
  private final ConcurrentHashMap<UUID, ReentrantLock> workspaceRebuildLocks = new ConcurrentHashMap<>();

  public RebuildService(
      KnowledgeDocumentRepository documentRepository,
      IngestionJobRepository jobRepository,
      IngestionService ingestionService,
      EmbeddingProvider embeddingProvider,
      WorkspaceLookup workspaceLookup,
      AtlasProperties properties) {
    this.documentRepository = documentRepository;
    this.jobRepository = jobRepository;
    this.ingestionService = ingestionService;
    this.embeddingProvider = embeddingProvider;
    this.workspaceLookup = workspaceLookup;
    this.properties = properties;
  }

  public IndexHealthResponse getIndexHealth(UUID workspaceId) {
    workspaceLookup.requireExists(workspaceId);

    String activeModel = embeddingProvider.embeddingModelName();
    int activeDims = embeddingProvider.embeddingDimensions();

    WorkspaceLookup.EmbeddingIdentity indexedIdentity = workspaceLookup.embeddingIdentity(workspaceId);

    List<KnowledgeDocument> docs = documentRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId);
    List<DocumentHealthItem> documentItems = new ArrayList<>();

    int readyCount = 0;
    int staleCount = 0;
    int pendingCount = 0;
    int failedCount = 0;

    for (KnowledgeDocument doc : docs) {
      DocumentHealthStatus health = DocumentHealthStatus.calculate(
          doc.ingestionStatus(),
          doc.embeddingModel(),
          doc.embeddingDimensions(),
          activeModel,
          activeDims);

      switch (health) {
        case READY -> readyCount++;
        case STALE -> staleCount++;
        case PENDING -> pendingCount++;
        case FAILED -> failedCount++;
      }

      documentItems.add(new DocumentHealthItem(
          doc.id(),
          doc.originalFilename(),
          doc.ingestionStatus().name(),
          health.name(),
          doc.embeddingModel(),
          doc.embeddingDimensions(),
          doc.failureReason()));
    }

    String overallStatus = "READY";
    if (failedCount > 0) {
      overallStatus = "FAILED";
    } else if (pendingCount > 0) {
      overallStatus = "PENDING";
    } else if (staleCount > 0 || (docs.isEmpty() && indexedIdentity != null && !activeModel.equals(indexedIdentity.model()))) {
      overallStatus = "STALE";
    }

    return new IndexHealthResponse(
        workspaceId,
        new EmbeddingIdentityDto(activeModel, activeDims),
        indexedIdentity != null ? new EmbeddingIdentityDto(indexedIdentity.model(), indexedIdentity.dimensions()) : null,
        overallStatus,
        docs.size(),
        readyCount,
        staleCount,
        pendingCount,
        failedCount,
        documentItems);
  }

  public RebuildResponse rebuildWorkspace(UUID workspaceId) {
    workspaceLookup.requireExists(workspaceId);

    ReentrantLock lock = workspaceRebuildLocks.computeIfAbsent(workspaceId, id -> new ReentrantLock());
    if (!lock.tryLock()) {
      throw new ApiException(HttpStatus.CONFLICT, "REBUILD_IN_PROGRESS",
          "A rebuild is already in progress for this workspace. Please wait and retry.");
    }
    try {
      String activeModel = embeddingProvider.embeddingModelName();
      int activeDims = embeddingProvider.embeddingDimensions();

      List<KnowledgeDocument> docs = documentRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId);
      if (docs.isEmpty()) {
        return new RebuildResponse(
            workspaceId,
            "COMPLETED",
            0,
            0,
            0,
            new EmbeddingIdentityDto(activeModel, activeDims),
            List.of());
      }

      int rebuiltCount = 0;
      int failedCount = 0;
      List<RebuildErrorItem> errors = new ArrayList<>();

      for (KnowledgeDocument doc : docs) {
        try {
          IngestionJob job = jobRepository.save(new IngestionJob(doc.id()));
          ingestionService.executeJob(job.id());

          KnowledgeDocument updatedDoc = documentRepository.findById(doc.id()).orElse(doc);
          if (updatedDoc.ingestionStatus() == IngestionStatus.COMPLETE) {
            rebuiltCount++;
          } else {
            failedCount++;
            errors.add(new RebuildErrorItem(
                doc.id(),
                doc.originalFilename(),
                updatedDoc.failureReason() != null ? updatedDoc.failureReason() : "Rebuild failed"));
          }
        } catch (Exception e) {
          log.error("Error rebuilding document {} in workspace {}", doc.id(), workspaceId, e);
          failedCount++;
          errors.add(new RebuildErrorItem(
              doc.id(),
              doc.originalFilename(),
              e.getMessage() != null ? e.getMessage() : "Rebuild exception"));
        }
      }

      String status = failedCount == 0 ? "COMPLETED" : (rebuiltCount > 0 ? "PARTIAL_FAILURE" : "FAILED");
      if (rebuiltCount > 0) {
        workspaceLookup.stampEmbeddingIdentity(workspaceId, activeModel, activeDims);
      }

      return new RebuildResponse(
          workspaceId,
          status,
          docs.size(),
          rebuiltCount,
          failedCount,
          new EmbeddingIdentityDto(activeModel, activeDims),
          errors);
    } finally {
      lock.unlock();
    }
  }

  public record EmbeddingIdentityDto(String model, int dimensions) {}

  public record DocumentHealthItem(
      UUID id,
      String originalFilename,
      String ingestionStatus,
      String healthStatus,
      String embeddingModel,
      Integer embeddingDimensions,
      String errorMessage) {}

  public record IndexHealthResponse(
      UUID workspaceId,
      EmbeddingIdentityDto activeEmbeddingIdentity,
      EmbeddingIdentityDto indexedEmbeddingIdentity,
      String status,
      int totalDocuments,
      int readyDocuments,
      int staleDocuments,
      int pendingDocuments,
      int failedDocuments,
      List<DocumentHealthItem> documents) {}

  public record RebuildErrorItem(UUID documentId, String filename, String errorMessage) {}

  public record RebuildResponse(
      UUID workspaceId,
      String status,
      int totalProcessed,
      int rebuiltCount,
      int failedCount,
      EmbeddingIdentityDto activeEmbeddingIdentity,
      List<RebuildErrorItem> errors) {}
}
