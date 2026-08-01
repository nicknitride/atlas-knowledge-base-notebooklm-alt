package dev.atlas.providers;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class DefaultLlmProviderFailClosedTest {
  @Test
  void streamFailsWhenLocalProviderUnreachable() throws Exception {
    DefaultLlmProvider provider = new DefaultLlmProvider(new ObjectMapper(), WebClient.builder());
    var typeField = DefaultLlmProvider.class.getDeclaredField("providerType");
    typeField.setAccessible(true);
    typeField.set(provider, "local");
    var urlField = DefaultLlmProvider.class.getDeclaredField("ollamaUrl");
    urlField.setAccessible(true);
    urlField.set(provider, "http://127.0.0.1:1");
    var modelField = DefaultLlmProvider.class.getDeclaredField("ollamaModel");
    modelField.setAccessible(true);
    modelField.set(provider, "llama3");

    AtomicReference<Throwable> error = new AtomicReference<>();
    provider.stream(
        List.of(new LlmProvider.ChatMessage("user", "hello")),
        chunk -> fail("Should not emit offline fallback chunks"),
        () -> fail("Should not complete successfully"),
        error::set);

    // Allow async WebClient error to surface
    long deadline = System.currentTimeMillis() + 15_000;
    while (error.get() == null && System.currentTimeMillis() < deadline) {
      Thread.sleep(50);
    }
    assertNotNull(error.get());
    assertFalse(error.get().getMessage().contains("Based on the workspace document sources"));
  }
}
