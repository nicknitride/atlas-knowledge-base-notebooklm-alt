package dev.atlas.providers;

import java.util.List;
import java.util.function.Consumer;

public interface LlmProvider {
  record ChatMessage(String role, String content) {}
  
  String generate(List<ChatMessage> messages);
  void stream(List<ChatMessage> messages, Consumer<String> chunkConsumer, Runnable onComplete, Consumer<Throwable> onError);
  String providerName();
}
