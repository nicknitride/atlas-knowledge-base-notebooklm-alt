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

  protected Workspace() {}

  Workspace(String name) { this.name = name; }
  UUID id() { return id; }
  String name() { return name; }
  Instant createdAt() { return createdAt; }
  void rename(String name) { this.name = name; }
}
