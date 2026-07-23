package dev.atlas.providers;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DefaultLlmProvider implements LlmProvider {
  private static final Logger log = LoggerFactory.getLogger(DefaultLlmProvider.class);

  @Value("${atlas.provider.type:local}")
  private String providerType;

  @Value("${atlas.provider.ollama.url:http://localhost:11434}")
  private String ollamaUrl;

  @Value("${atlas.provider.ollama.model:llama3}")
  private String ollamaModel;

  @Value("${atlas.provider.openai.api-key:}")
  private String openAiApiKey;

  @Value("${atlas.provider.openai.model:gpt-4o-mini}")
  private String openAiModel;

  @Value("${atlas.provider.gemini.api-key:}")
  private String geminiApiKey;

  @Value("${atlas.provider.gemini.model:gemini-2.0-flash}")
  private String geminiModel;

  @Override
  public String providerName() {
    return providerType;
  }

  @Override
  public String generate(List<ChatMessage> messages) {
    if ("ollama".equalsIgnoreCase(providerType)) {
      try {
        return generateOllama(messages);
      } catch (Exception e) {
        log.warn("Ollama LLM call failed, using fallback synthesis: {}", e.getMessage());
      }
    } else if ("openai".equalsIgnoreCase(providerType) && !openAiApiKey.isBlank()) {
      try {
        return generateOpenAi(messages);
      } catch (Exception e) {
        log.warn("OpenAI LLM call failed, using fallback synthesis: {}", e.getMessage());
      } else if ("gemini".equalsIgnoreCase(providerType) && !geminiApiKey.isBlank()) {
          try { return generateGemini(messages); }
          catch (Exception e) { log.warn("Gemini call failed: {}", e.getMessage()); }
    }
    return generateFallback(messages);
  }

  @Override
  public void stream(List<ChatMessage> messages, Consumer<String> chunkConsumer, Runnable onComplete, Consumer<Throwable> onError) {
    try {
      String fullResponse = generate(messages);
      String[] words = fullResponse.split("(?<=\\s)|(?=\\s)");
      for (String word : words) {
        chunkConsumer.accept(word);
        Thread.sleep(15);
      }
      onComplete.run();
    } catch (Exception e) {
      onError.accept(e);
    }
  }

  private String generateFallback(List<ChatMessage> messages) {
    String userPrompt = "";
    String context = "";
    for (ChatMessage msg : messages) {
      if ("user".equals(msg.role())) {
        userPrompt = msg.content();
      } else if ("system".equals(msg.role())) {
        context = msg.content();
      }
    }

    if (context.contains("No relevant documents or sources were found")) {
      return "I do not have sufficient information in the uploaded workspace documents to answer this question. Please upload relevant source material or refine your query.";
    }

    // Extract excerpt from system context if available
    StringBuilder answer = new StringBuilder();
    answer.append("Based on the workspace document sources provided:\n\n");
    
    int contextStart = context.indexOf("=== RETRIEVED SOURCES ===");
    if (contextStart != -1) {
      String sourcesSection = context.substring(contextStart);
      String[] lines = sourcesSection.split("\n");
      int citationCount = 0;
      for (String line : lines) {
        if (line.startsWith("[Source") || line.startsWith("- Content:")) {
          if (line.startsWith("[Source")) {
            citationCount++;
            answer.append("\n**").append(line.trim()).append("**:\n");
          } else if (line.startsWith("- Content:")) {
            String contentSnippet = line.substring("- Content:".length()).trim();
            if (contentSnippet.length() > 300) {
              contentSnippet = contentSnippet.substring(0, 300) + "...";
            }
            answer.append(contentSnippet).append(" [").append(citationCount).append("]\n");
          }
        }
      }
    } else {
      answer.append("Synthesized response grounded in the provided document context.");
    }
    return answer.toString();
  }

  private String generateOllama(List<ChatMessage> messages) throws Exception {
    URI uri = URI.create(ollamaUrl + "/api/chat");
    HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
    conn.setRequestMethod("POST");
    conn.setDoOutput(true);
    conn.setRequestProperty("Content-Type", "application/json");

    StringBuilder body = new StringBuilder();
    body.append("{\"model\":\"").append(ollamaModel).append("\",\"stream\":false,\"messages\":[");
    for (int i = 0; i < messages.size(); i++) {
      ChatMessage m = messages.get(i);
      body.append(String.format("{\"role\":\"%s\",\"content\":%s}", m.role(), escapeJson(m.content())));
      if (i < messages.size() - 1) body.append(",");
    }
    body.append("]}");

    try (OutputStream os = conn.getOutputStream()) {
      os.write(body.toString().getBytes(StandardCharsets.UTF_8));
    }
    if (conn.getResponseCode() != 200) {
      throw new RuntimeException("Ollama status " + conn.getResponseCode());
    }
    String response;
    try (InputStream is = conn.getInputStream()) {
      response = new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }
    int contentIdx = response.indexOf("\"content\":\"");
    if (contentIdx != -1) {
      int start = contentIdx + "\"content\":\"".length();
      int end = response.indexOf("\"", start);
      return response.substring(start, end).replace("\\n", "\n").replace("\\\"", "\"");
    }
    return response;
  }

  private String generateOpenAi(List<ChatMessage> messages) throws Exception {
    URI uri = URI.create("https://api.openai.com/v1/chat/completions");
    HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
    conn.setRequestMethod("POST");
    conn.setDoOutput(true);
    conn.setRequestProperty("Content-Type", "application/json");
    conn.setRequestProperty("Authorization", "Bearer " + openAiApiKey);

    StringBuilder body = new StringBuilder();
    body.append("{\"model\":\"").append(openAiModel).append("\",\"messages\":[");
    for (int i = 0; i < messages.size(); i++) {
      ChatMessage m = messages.get(i);
      body.append(String.format("{\"role\":\"%s\",\"content\":%s}", m.role(), escapeJson(m.content())));
      if (i < messages.size() - 1) body.append(",");
    }
    body.append("]}");

    try (OutputStream os = conn.getOutputStream()) {
      os.write(body.toString().getBytes(StandardCharsets.UTF_8));
    }
    if (conn.getResponseCode() != 200) {
      throw new RuntimeException("OpenAI status " + conn.getResponseCode());
    }
    String response;
    try (InputStream is = conn.getInputStream()) {
      response = new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }
    int contentIdx = response.indexOf("\"content\":\"");
    if (contentIdx != -1) {
      int start = contentIdx + "\"content\":\"".length();
      int end = response.indexOf("\"", start);
      return response.substring(start, end).replace("\\n", "\n").replace("\\\"", "\"");
    }
    return response;
  }

  private String escapeJson(String text) {
    return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\"";
  }
}
