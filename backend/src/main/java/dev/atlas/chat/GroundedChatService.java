package dev.atlas.chat;

import dev.atlas.providers.LlmProvider;
import dev.atlas.retrieval.RetrievedChunk;
import dev.atlas.retrieval.VectorSearchService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
        .orElseThrow(() -> new IllegalArgumentException("Conversation not found in workspace"));

    // Save user message
    Message userMessage = messageRepository.save(new Message(conversationId, "USER", userQuery));

    // Retrieve relevant workspace chunks
    List<RetrievedChunk> chunks = filterByRelativeScore(workspaceId,userQuery, MAXDROPCONST);


    // Build system prompt
    List<LlmProvider.ChatMessage> promptMessages = buildPromptMessages(conversationId, userQuery, chunks);

    // Call LLM
    String assistantAnswer = llmProvider.generate(promptMessages);

    // Save assistant message
    Message assistantMessage = messageRepository.save(new Message(conversationId, "ASSISTANT", assistantAnswer));

    // Save citations
    List<CitationResponse> citations = saveCitations(assistantMessage.id(), chunks);

    // Update conversation timestamp
    conversation.touch();
    conversationRepository.save(conversation);

    return new ChatResult(assistantMessage, citations);
  }

  private List<RetrievedChunk> filterByRelativeScore(UUID workspaceId, String userQuery,double maxDrop) {
    List<RetrievedChunk> candidates = retrievalService.search(workspaceId, userQuery, 15, 0.10);
    log.info("Retrieved {} candidates", candidates.size());
    candidates.forEach(c ->
            log.info("{} -> {}", c.documentFilename(), c.similarity()));
    if (candidates.isEmpty() || workspaceId.toString().trim().isEmpty() || userQuery.isEmpty() ) return List.of();

    double topScore = candidates.get(0).similarity();
    // double minimumAcceptable = Math.max(0.50, topScore - maxDrop); // absolute floor or relative gap
    double minimumAcceptable = topScore * 0.9;
    log.info("Top score: {}", topScore);
    log.info("Minimum acceptable: {}", minimumAcceptable);
    List<RetrievedChunk> filtered = candidates.stream()
            .filter(c -> c.similarity() >= minimumAcceptable)
            .toList();
    log.info("Remaining after filter: {}", filtered.size());
    return filtered;
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
          .orElseThrow(() -> new IllegalArgumentException("Conversation not found in workspace"));

      // Save user message
      messageRepository.save(new Message(conversationId, "USER", userQuery));

      // Retrieve relevant workspace chunks
      List<RetrievedChunk> chunks = filterByRelativeScore(workspaceId, userQuery, MAXDROPCONST);

      // Build system prompt
      List<LlmProvider.ChatMessage> promptMessages = buildPromptMessages(conversationId, userQuery, chunks);

      StringBuilder fullAnswer = new StringBuilder();
      llmProvider.stream(
          promptMessages,
          chunk -> {
            fullAnswer.append(chunk);
            chunkConsumer.accept(chunk);
          },
          () -> {
            // Save assistant message
            String answerText = fullAnswer.toString().trim();
            if (answerText.isEmpty()) {
              answerText = "I could not generate a response based on the workspace sources provided.";
            }
            Message assistantMessage = messageRepository.save(new Message(conversationId, "ASSISTANT", answerText));
            List<CitationResponse> citations = saveCitations(assistantMessage.id(), chunks);
            citationConsumer.accept(citations);

            conversation.touch();
            conversationRepository.save(conversation);
            onComplete.run();
          },
          onError
      );
    } catch (Exception e) {
      onError.accept(e);
    }
  }

  private List<LlmProvider.ChatMessage> buildPromptMessages(UUID conversationId, String userQuery, List<RetrievedChunk> chunks) {
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
        systemPrompt.append(String.format("[Source %d: %s (ordinal %d)]\n- Content: %s\n\n",
            i + 1, c.documentFilename(), c.ordinal(), c.content()));
      }
    }

    promptMessages.add(new LlmProvider.ChatMessage("system", systemPrompt.toString()));

    // Load recent history safely
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
      try {
        jdbc.update(
            "INSERT INTO message_citations (message_id, chunk_id, ordinal) VALUES (?, ?, ?)",
            messageId, chunk.chunkId(), i + 1
        );
      } catch (Exception e) {
        log.warn("Failed to insert citation link for message {} and chunk {}: {}", messageId, chunk.chunkId(), e.getMessage());
      }
      citations.add(new CitationResponse(
          chunk.chunkId(),
          chunk.documentId(),
          chunk.documentFilename(),
          chunk.ordinal(),
          chunk.sourceLocator(),
          chunk.content(),
          chunk.similarity()
      ));
      log.info("Sending citation similarity: {}", chunk.similarity());
    }
    return citations;
  }

  public record ChatResult(Message message, List<CitationResponse> citations) {}
}
