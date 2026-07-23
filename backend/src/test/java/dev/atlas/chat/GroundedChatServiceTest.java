package dev.atlas.chat;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.atlas.providers.LlmProvider;
import dev.atlas.retrieval.RetrievedChunk;
import dev.atlas.retrieval.VectorSearchService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class GroundedChatServiceTest {
  private VectorSearchService retrievalService;
  private LlmProvider llmProvider;
  private MessageRepository messageRepository;
  private ConversationRepository conversationRepository;
  private JdbcTemplate jdbc;
  private GroundedChatService chatService;

  @BeforeEach
  void setUp() {
    retrievalService = mock(VectorSearchService.class);
    llmProvider = mock(LlmProvider.class);
    messageRepository = mock(MessageRepository.class);
    conversationRepository = mock(ConversationRepository.class);
    jdbc = mock(JdbcTemplate.class);
    chatService = new GroundedChatService(retrievalService, llmProvider, messageRepository, conversationRepository, jdbc);
  }

  @Test
  void testGroundedChatWithRetrievedSources() {
    UUID workspaceId = UUID.randomUUID();
    UUID conversationId = UUID.randomUUID();
    Conversation conversation = new Conversation(workspaceId, "Test Chat");

    when(conversationRepository.findByIdAndWorkspaceId(conversationId, workspaceId))
        .thenReturn(Optional.of(conversation));
    when(messageRepository.save(any(Message.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    RetrievedChunk chunk = new RetrievedChunk(
        UUID.randomUUID(), UUID.randomUUID(), "doc.txt", 0, "Quarterly revenue was $50M.", "{\"location\":\"Page 1\"}", 0.95
    );
    when(retrievalService.search(any(UUID.class), anyString(), anyInt(), anyDouble()))
        .thenReturn(List.of(chunk));
    when(llmProvider.generate(anyList()))
        .thenReturn("The quarterly revenue was $50M [1].");

    GroundedChatService.ChatResult result = chatService.chat(workspaceId, conversationId, "What was the revenue?");

    assertNotNull(result);
    assertEquals("ASSISTANT", result.message().role());
    assertEquals("The quarterly revenue was $50M [1].", result.message().content());
    assertEquals(1, result.citations().size());
    assertEquals("doc.txt", result.citations().get(0).documentFilename());
  }

  @Test
  void testGroundedChatRefusesWhenNoSourcesFound() {
    UUID workspaceId = UUID.randomUUID();
    UUID conversationId = UUID.randomUUID();
    Conversation conversation = new Conversation(workspaceId, "Test Chat");

    when(conversationRepository.findByIdAndWorkspaceId(conversationId, workspaceId))
        .thenReturn(Optional.of(conversation));
    when(messageRepository.save(any(Message.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    when(retrievalService.search(any(UUID.class), anyString(), anyInt(), anyDouble()))
        .thenReturn(List.of());
    when(llmProvider.generate(anyList()))
        .thenReturn("I do not have sufficient information in the uploaded workspace documents to answer this question.");

    GroundedChatService.ChatResult result = chatService.chat(workspaceId, conversationId, "Unknown query");

    assertNotNull(result);
    assertNotNull(result.message());
    assertNotNull(result.message().content());
    assertTrue(result.message().content().contains("sufficient information"));
    assertTrue(result.citations().isEmpty());
  }
}
