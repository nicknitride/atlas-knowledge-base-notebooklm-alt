package dev.atlas.documents;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "documents")
class KnowledgeDocument {
  @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
  @Column(name = "workspace_id", nullable = false) private UUID workspaceId;
  @Column(name = "original_filename", nullable = false) private String originalFilename;
  @Column(name = "content_type", nullable = false) private String contentType;
  @Column(name = "storage_key", nullable = false) private String storageKey;
  @Enumerated(EnumType.STRING) @Column(name = "ingestion_status", nullable = false) private IngestionStatus ingestionStatus;
  @Column(name = "failure_reason") private String failureReason;
  @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
  @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();
  protected KnowledgeDocument() {}
  KnowledgeDocument(UUID workspaceId, String filename, String contentType, String storageKey) {
    this.workspaceId = workspaceId; this.originalFilename = filename; this.contentType = contentType; this.storageKey = storageKey;
    this.ingestionStatus = IngestionStatus.PENDING;
  }
  UUID id() { return id; } UUID workspaceId() { return workspaceId; } String originalFilename() { return originalFilename; }
  String contentType() { return contentType; } String storageKey() { return storageKey; } IngestionStatus ingestionStatus() { return ingestionStatus; }
  String failureReason() { return failureReason; } Instant createdAt() { return createdAt; }
  void markProcessing() { ingestionStatus = IngestionStatus.PROCESSING; touch(); }
  void markComplete() { ingestionStatus = IngestionStatus.COMPLETE; failureReason = null; touch(); }
  void markFailed(String reason) { ingestionStatus = IngestionStatus.FAILED; failureReason = reason; touch(); }
  private void touch() { updatedAt = Instant.now(); }
}
