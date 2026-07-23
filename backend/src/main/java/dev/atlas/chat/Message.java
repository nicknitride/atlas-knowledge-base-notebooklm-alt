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
@Table(name = "messages")
public class Message {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "conversation_id", nullable = false)
  private UUID conversationId;

  @Column(nullable = false, length = 16)
  private String role;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String content;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  protected Message() {}

  public Message(UUID conversationId, String role, String content) {
    this.conversationId = conversationId;
    this.role = role;
    this.content = content;
  }

  public UUID id() { return id; }
  public UUID conversationId() { return conversationId; }
  public String role() { return role; }
  public String content() { return content; }
  public Instant createdAt() { return createdAt; }
}
