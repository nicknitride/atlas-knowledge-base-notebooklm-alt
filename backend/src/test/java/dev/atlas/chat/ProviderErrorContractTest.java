package dev.atlas.chat;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.atlas.providers.LlmProvider;
import dev.atlas.retrieval.VectorSearchService;
import dev.atlas.support.ApiException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

class ProviderErrorContractTest {
  @Test
  void chatMapsProviderDownToProviderUnavailable() {
    VectorSearchService retrieval = mock(VectorSearchService.class);
    LlmProvider llm = mock(LlmProvider.class);
    MessageRepository messages = mock(MessageRepository.class);
    ConversationRepository conversations = mock(ConversationRepository.class);
    when(conversations.findByIdAndWorkspaceId(any(), any()))
        .thenReturn(Optional.of(new Conversation(UUID.randomUUID(), "c")));
    when(messages.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(retrieval.search(any(), anyString(), anyInt(), anyDouble())).thenReturn(java.util.List.of());
    when(llm.generate(anyList())).thenThrow(new IllegalStateException("connection refused"));

    GroundedChatService service = new GroundedChatService(
        retrieval, llm, messages, conversations, mock(JdbcTemplate.class));

    ApiException ex = assertThrows(
        ApiException.class,
        () -> service.chat(UUID.randomUUID(), UUID.randomUUID(), "hi"));
    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.status());
    assertEquals("PROVIDER_UNAVAILABLE", ex.code());
  }
}
