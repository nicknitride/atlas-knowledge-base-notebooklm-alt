package dev.atlas.documents;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ingestion_jobs")
public class IngestionJob {
  @Id
  private UUID id;

  @Column(name = "document_id", nullable = false)
  private UUID documentId;

  @Column(name = "status", nullable = false)
  private String status;

  @Column(name = "error_message")
  private String errorMessage;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "started_at")
  private Instant startedAt;

  protected IngestionJob() {}

  public IngestionJob(UUID documentId) {
    this.id = UUID.randomUUID();
    this.documentId = documentId;
    this.status = "PENDING";
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public UUID id() {
    return id;
  }

  public UUID documentId() {
    return documentId;
  }

  public String status() {
    return status;
  }

  public String errorMessage() {
    return errorMessage;
  }

  public Instant startedAt() {
    return startedAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  public void markProcessing() {
    this.status = "PROCESSING";
    this.startedAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public void markCompleted() {
    this.status = "COMPLETED";
    this.updatedAt = Instant.now();
  }

  public void markFailed(String message) {
    this.status = "FAILED";
    this.errorMessage = message;
    this.updatedAt = Instant.now();
  }

  public void markPending() {
    this.status = "PENDING";
    this.errorMessage = null;
    this.startedAt = null;
    this.updatedAt = Instant.now();
  }
}
