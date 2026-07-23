package dev.atlas.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "conversations")
public class Conversation {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "workspace_id", nullable = false)
  private UUID workspaceId;

  @Column(nullable = false, length = 240)
  private String title = "New conversation";

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  protected Conversation() {}

  public Conversation(UUID workspaceId, String title) {
    this.workspaceId = workspaceId;
    if (title != null && !title.isBlank()) {
      this.title = title;
    }
  }

  public UUID id() { return id; }
  public UUID workspaceId() { return workspaceId; }
  public String title() { return title; }
  public Instant createdAt() { return createdAt; }
  public Instant updatedAt() { return updatedAt; }

  public void rename(String title) {
    this.title = title;
    this.updatedAt = Instant.now();
  }

  public void touch() {
    this.updatedAt = Instant.now();
  }
}
