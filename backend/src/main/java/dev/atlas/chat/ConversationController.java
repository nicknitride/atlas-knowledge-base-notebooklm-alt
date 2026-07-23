package dev.atlas.chat;

import dev.atlas.workspaces.WorkspaceLookup;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/conversations")
public class ConversationController {
  private final WorkspaceLookup workspaces;
  private final ConversationRepository conversations;
  private final MessageRepository messages;
  private final GroundedChatService chatService;
  private final JdbcTemplate jdbc;

  public ConversationController(
      WorkspaceLookup workspaces,
      ConversationRepository conversations,
      MessageRepository messages,
      GroundedChatService chatService,
      JdbcTemplate jdbc) {
    this.workspaces = workspaces;
    this.conversations = conversations;
    this.messages = messages;
    this.chatService = chatService;
    this.jdbc = jdbc;
  }

  @GetMapping
  public List<ConversationResponse> list(@PathVariable UUID workspaceId) {
    workspaces.requireExists(workspaceId);
    return conversations.findByWorkspaceIdOrderByUpdatedAtDesc(workspaceId)
        .stream()
        .map(ConversationResponse::from)
        .toList();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ConversationResponse create(@PathVariable UUID workspaceId, @RequestBody(required = false) CreateConversationRequest request) {
    workspaces.requireExists(workspaceId);
    String title = (request != null && request.title() != null && !request.title().isBlank()) ? request.title().trim() : "New conversation";
    Conversation conversation = conversations.save(new Conversation(workspaceId, title));
    return ConversationResponse.from(conversation);
  }

  @GetMapping("/{id}")
  public ConversationDetailResponse get(@PathVariable UUID workspaceId, @PathVariable UUID id) {
    workspaces.requireExists(workspaceId);
    Conversation conversation = conversations.findByIdAndWorkspaceId(id, workspaceId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
    List<Message> msgList = messages.findByConversationIdOrderByCreatedAtAsc(id);
    List<MessageWithCitations> msgWithCitations = msgList.stream().map(m -> {
      List<CitationResponse> citations = fetchCitations(m.id());
      return new MessageWithCitations(m.id(), m.role(), m.content(), m.createdAt(), citations);
    }).toList();
    return new ConversationDetailResponse(conversation.id(), conversation.workspaceId(), conversation.title(), conversation.createdAt(), conversation.updatedAt(), msgWithCitations);
  }

  @PutMapping("/{id}")
  public ConversationResponse rename(@PathVariable UUID workspaceId, @PathVariable UUID id, @Valid @RequestBody RenameConversationRequest request) {
    workspaces.requireExists(workspaceId);
    Conversation conversation = conversations.findByIdAndWorkspaceId(id, workspaceId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
    conversation.rename(request.title().trim());
    return ConversationResponse.from(conversations.save(conversation));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID workspaceId, @PathVariable UUID id) {
    workspaces.requireExists(workspaceId);
    Conversation conversation = conversations.findByIdAndWorkspaceId(id, workspaceId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
    conversations.delete(conversation);
  }

  @PostMapping("/{id}/messages")
  public MessageWithCitations sendMessage(
      @PathVariable UUID workspaceId,
      @PathVariable UUID id,
      @Valid @RequestBody SendMessageRequest request) {
    workspaces.requireExists(workspaceId);
    GroundedChatService.ChatResult result = chatService.chat(workspaceId, id, request.content());
    return new MessageWithCitations(
        result.message().id(),
        result.message().role(),
        result.message().content(),
        result.message().createdAt(),
        result.citations()
    );
  }

  @GetMapping(value = "/{id}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter streamMessage(
      @PathVariable UUID workspaceId,
      @PathVariable UUID id,
      @RequestParam("query") String query) {
    workspaces.requireExists(workspaceId);
    SseEmitter emitter = new SseEmitter(60000L);

    chatService.streamChat(
        workspaceId,
        id,
        query,
        chunk -> {
          try {
            emitter.send(SseEmitter.event().name("chunk").data(chunk));
          } catch (IOException e) {
            emitter.completeWithError(e);
          }
        },
        citations -> {
          try {
            emitter.send(SseEmitter.event().name("citations").data(citations));
          } catch (IOException e) {
            emitter.completeWithError(e);
          }
        },
        () -> {
          try {
            emitter.send(SseEmitter.event().name("done").data("[DONE]"));
            emitter.complete();
          } catch (IOException e) {
            emitter.completeWithError(e);
          }
        },
        error -> emitter.completeWithError(error)
    );

    return emitter;
  }

  private List<CitationResponse> fetchCitations(UUID messageId) {
    String sql = """
        SELECT mc.ordinal,
               c.id AS chunk_id,
               c.document_id,
               d.original_filename,
               c.ordinal AS chunk_ordinal,
               c.source_locator,
               c.content
        FROM message_citations mc
        JOIN document_chunks c ON mc.chunk_id = c.id
        JOIN documents d ON c.document_id = d.id
        WHERE mc.message_id = ?
        ORDER BY mc.ordinal ASC
        """;
    try {
      return jdbc.query(sql, (rs, rowNum) -> new CitationResponse(
          UUID.fromString(rs.getString("chunk_id")),
          UUID.fromString(rs.getString("document_id")),
          rs.getString("original_filename"),
          rs.getInt("chunk_ordinal"),
          rs.getString("source_locator"),
          rs.getString("content"),
          1.0
      ), messageId);
    } catch (Exception e) {
      return new ArrayList<>();
    }
  }

  public record CreateConversationRequest(String title) {}
  public record RenameConversationRequest(@NotBlank String title) {}
  public record SendMessageRequest(@NotBlank String content) {}

  public record ConversationResponse(UUID id, UUID workspaceId, String title, Instant createdAt, Instant updatedAt) {
    static ConversationResponse from(Conversation c) {
      return new ConversationResponse(c.id(), c.workspaceId(), c.title(), c.createdAt(), c.updatedAt());
    }
  }

  public record ConversationDetailResponse(UUID id, UUID workspaceId, String title, Instant createdAt, Instant updatedAt, List<MessageWithCitations> messages) {}
  public record MessageWithCitations(UUID id, String role, String content, Instant createdAt, List<CitationResponse> citations) {}
}
