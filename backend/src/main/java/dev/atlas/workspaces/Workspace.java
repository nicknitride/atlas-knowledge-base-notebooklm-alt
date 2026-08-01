package dev.atlas.workspaces;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workspaces")
class Workspace {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "embedding_model", length = 120)
  private String embeddingModel;

  @Column(name = "embedding_dimensions")
  private Integer embeddingDimensions;

  protected Workspace() {}

  Workspace(String name) {
    this.name = name;
  }

  UUID id() {
    return id;
  }

  String name() {
    return name;
  }

  Instant createdAt() {
    return createdAt;
  }

  String embeddingModel() {
    return embeddingModel;
  }

  Integer embeddingDimensions() {
    return embeddingDimensions;
  }

  void rename(String name) {
    this.name = name;
  }

  void setEmbeddingIdentity(String model, int dimensions) {
    this.embeddingModel = model;
    this.embeddingDimensions = dimensions;
  }

  void clearEmbeddingIdentity() {
    this.embeddingModel = null;
    this.embeddingDimensions = null;
  }

  boolean hasEmbeddingIdentity() {
    return embeddingModel != null && !embeddingModel.isBlank() && embeddingDimensions != null;
  }
}
