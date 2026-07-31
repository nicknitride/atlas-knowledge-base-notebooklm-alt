package dev.atlas.chat;

import dev.atlas.providers.LlmProvider;
import dev.atlas.retrieval.RetrievedChunk;
import dev.atlas.retrieval.VectorSearchService;
import dev.atlas.support.ApiException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroundedChatService {
  private static final Logger log = LoggerFactory.getLogger(GroundedChatService.class);

  private final VectorSearchService retrievalService;
  private final LlmProvider llmProvider;
  private final MessageRepository messageRepository;
  private final ConversationRepository conversationRepository;
  private final JdbcTemplate jdbc;
  private static final float MAXDROPCONST = 0.40F;

  public GroundedChatService(
      VectorSearchService retrievalService,
      LlmProvider llmProvider,
      MessageRepository messageRepository,
      ConversationRepository conversationRepository,
      JdbcTemplate jdbc) {
    this.retrievalService = retrievalService;
    this.llmProvider = llmProvider;
    this.messageRepository = messageRepository;
    this.conversationRepository = conversationRepository;
    this.jdbc = jdbc;
  }

  @Transactional
  public ChatResult chat(UUID workspaceId, UUID conversationId, String userQuery) {
    Conversation conversation = conversationRepository.findByIdAndWorkspaceId(conversationId, workspaceId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Conversation not found in workspace"));

    messageRepository.saveAndFlush(new Message(conversationId, "USER", userQuery));

    List<RetrievedChunk> chunks = filterByRelativeScore(workspaceId, userQuery, MAXDROPCONST);
    List<LlmProvider.ChatMessage> promptMessages = buildPromptMessages(conversationId, userQuery, chunks);

    String assistantAnswer;
    try {
      assistantAnswer = llmProvider.generate(promptMessages);
    } catch (Exception e) {
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "PROVIDER_UNAVAILABLE",
          "AI backend is unavailable. Check the local AI endpoint and model.");
    }

    Message assistantMessage =
        messageRepository.saveAndFlush(new Message(conversationId, "ASSISTANT", assistantAnswer));
    List<CitationResponse> citations = saveCitations(assistantMessage.id(), chunks);

    conversation.touch();
    conversationRepository.save(conversation);

    return new ChatResult(assistantMessage, citations);
  }

  private List<RetrievedChunk> filterByRelativeScore(UUID workspaceId, String userQuery, double maxDrop) {
    List<RetrievedChunk> candidates = retrievalService.search(workspaceId, userQuery, 15, 0.10);
    log.info("Retrieved {} candidates", candidates.size());
    if (candidates.isEmpty() || userQuery == null || userQuery.isBlank()) {
      return List.of();
    }

    double topScore = candidates.get(0).similarity();
    double minimumAcceptable = topScore * 0.9;
    return candidates.stream().filter(c -> c.similarity() >= minimumAcceptable).toList();
  }

  public void streamChat(
      UUID workspaceId,
      UUID conversationId,
      String userQuery,
      Consumer<String> chunkConsumer,
      Consumer<List<CitationResponse>> citationConsumer,
      Runnable onComplete,
      Consumer<Throwable> onError) {
    try {
      Conversation conversation = conversationRepository.findByIdAndWorkspaceId(conversationId, workspaceId)
          .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Conversation not found in workspace"));

      messageRepository.saveAndFlush(new Message(conversationId, "USER", userQuery));

      List<RetrievedChunk> chunks = filterByRelativeScore(workspaceId, userQuery, MAXDROPCONST);
      List<LlmProvider.ChatMessage> promptMessages = buildPromptMessages(conversationId, userQuery, chunks);

      StringBuilder fullAnswer = new StringBuilder();
      llmProvider.stream(
          promptMessages,
          chunk -> {
            fullAnswer.append(chunk);
            chunkConsumer.accept(chunk);
          },
          () -> {
            String answerText = fullAnswer.toString().trim();
            if (answerText.isEmpty()) {
              onError.accept(new ApiException(
                  HttpStatus.SERVICE_UNAVAILABLE,
                  "PROVIDER_UNAVAILABLE",
                  "AI backend returned an empty response"));
              return;
            }
            Message assistantMessage =
                messageRepository.saveAndFlush(new Message(conversationId, "ASSISTANT", answerText));
            List<CitationResponse> citations = saveCitations(assistantMessage.id(), chunks);
            citationConsumer.accept(citations);

            conversation.touch();
            conversationRepository.save(conversation);
            onComplete.run();
          },
          error -> {
            if (error instanceof ApiException apiException) {
              onError.accept(apiException);
            } else {
              onError.accept(new ApiException(
                  HttpStatus.SERVICE_UNAVAILABLE,
                  "PROVIDER_UNAVAILABLE",
                  "AI backend is unavailable. Check the local AI endpoint and model."));
            }
          });
    } catch (ApiException e) {
      onError.accept(e);
    } catch (Exception e) {
      onError.accept(new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "PROVIDER_UNAVAILABLE",
          "AI backend is unavailable. Check the local AI endpoint and model."));
    }
  }

  private List<LlmProvider.ChatMessage> buildPromptMessages(
      UUID conversationId, String userQuery, List<RetrievedChunk> chunks) {
    List<LlmProvider.ChatMessage> promptMessages = new ArrayList<>();

    StringBuilder systemPrompt = new StringBuilder();
    systemPrompt.append("You are Atlas, a precise and grounded AI assistant for private knowledge workspaces.\n");
    systemPrompt.append("Instructions:\n");
    systemPrompt.append("1. Answer the user's query STRICTLY based on the provided retrieved workspace sources below.\n");
    systemPrompt.append("2. If no relevant sources are available or the sources do not contain sufficient evidence to answer, explicitly decline or qualify your response.\n");
    systemPrompt.append("3. Cite your sources using numeric references [1], [2], etc., corresponding to the listed retrieved chunks.\n\n");

    if (chunks.isEmpty()) {
      systemPrompt.append("=== RETRIEVED SOURCES ===\nNo relevant documents or sources were found in this workspace.\n");
    } else {
      systemPrompt.append("=== RETRIEVED SOURCES ===\n");
      for (int i = 0; i < chunks.size(); i++) {
        RetrievedChunk c = chunks.get(i);
        systemPrompt.append(String.format(
            "[Source %d: %s (ordinal %d)]\n- Content: %s\n\n",
            i + 1, c.documentFilename(), c.ordinal(), c.content()));
      }
    }

    promptMessages.add(new LlmProvider.ChatMessage("system", systemPrompt.toString()));

    List<Message> history = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
    if (history != null) {
      for (Message m : history) {
        if (!m.content().equals(userQuery)) {
          promptMessages.add(new LlmProvider.ChatMessage(m.role().toLowerCase(), m.content()));
        }
      }
    }

    promptMessages.add(new LlmProvider.ChatMessage("user", userQuery));
    return promptMessages;
  }

  private List<CitationResponse> saveCitations(UUID messageId, List<RetrievedChunk> chunks) {
    List<CitationResponse> citations = new ArrayList<>();
    if (chunks == null || chunks.isEmpty()) {
      return citations;
    }
    for (int i = 0; i < chunks.size(); i++) {
      RetrievedChunk chunk = chunks.get(i);
      if (chunk.documentId() == null || chunk.documentFilename() == null || chunk.content() == null) {
        continue;
      }
      try {
        jdbc.update(
            "INSERT INTO message_citations (message_id, chunk_id, ordinal) VALUES (?, ?, ?)",
            messageId, chunk.chunkId(), i + 1);
      } catch (Exception e) {
        log.warn("Failed to insert citation link for message {}: {}", messageId, e.getMessage());
      }
      citations.add(new CitationResponse(
          chunk.chunkId(),
          chunk.documentId(),
          chunk.documentFilename(),
          chunk.ordinal(),
          chunk.sourceLocator(),
          chunk.content(),
          chunk.similarity()));
    }
    return citations;
  }

  public record ChatResult(Message message, List<CitationResponse> citations) {}
}
