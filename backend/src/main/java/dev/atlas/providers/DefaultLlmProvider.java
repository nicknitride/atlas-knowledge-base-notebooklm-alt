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

  @Value("${atlas.provider.gemini.model:gemini-3.5-flash}")
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
    } 
    else if ("openai".equalsIgnoreCase(providerType) && !openAiApiKey.isBlank()) {
      try {
        return generateOpenAi(messages);
      } catch (Exception e) {
        log.warn("OpenAI LLM call failed, using fallback synthesis: {}", e.getMessage());
      }
    } else if ("gemini".equalsIgnoreCase(providerType) && !geminiApiKey.isBlank()) {
          try { 
            return generateGemini(messages); 
          }
          catch (Exception e) { 
            log.warn("Gemini call failed: {}", e.getMessage()); 
          }
    }
    return generateFallback(messages);
  }

  @Override
  public void stream(List<ChatMessage> messages, Consumer<String> chunkConsumer, Runnable onComplete, Consumer<Throwable> onError) {
    try {
      String fullResponse = generate(messages);
      // Split on word boundaries so each chunk is a word followed by its trailing
      // whitespace. This ensures spaces are never emitted as standalone SSE data
      // lines (which the SSE spec would strip), while still preserving spacing
      // between words when the frontend concatenates chunks.
      String[] words = fullResponse.split("(?<=\\s)(?=\\S)");
      for (String word : words) {
        if (!word.isEmpty()) {
          chunkConsumer.accept(word);
          Thread.sleep(15);
        }
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


  /**
   * Calls the Gemini Developer API (generateContent endpoint).
   *
   * Endpoint:
   *   POST https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={apiKey}
   *
   * Gemini role values: "user" and "model" (not "assistant").
   * System messages are passed via the systemInstruction field (v1beta).
   */
  private String generateGemini(List<ChatMessage> messages) throws Exception{
    URI uri = URI.create("https://generativelanguage.googleapis.com/v1beta/models/"
              + geminiModel + ":generateContent?key=" + geminiApiKey);
    HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
    conn.setRequestMethod("POST");
    conn.setDoOutput(true);
    conn.setRequestProperty("Content-Type", "application/json");

      // Separate system message from conversation turns
      String systemText = null;
      for (ChatMessage m : messages) {
          if ("system".equalsIgnoreCase(m.role())) systemText = m.content();
      }

      StringBuilder body = new StringBuilder("{");

      // Attach system instruction if present
      if (systemText != null && !systemText.isBlank()) {
          body.append("\"systemInstruction\":{\"parts\":[{\"text\":")
              .append(escapeJson(systemText))
              .append("}]},");
      }

      body.append("\"contents\":[");
      boolean first = true;
      for (ChatMessage m : messages) {
          if ("system".equalsIgnoreCase(m.role())) continue;
          String geminiRole = "assistant".equalsIgnoreCase(m.role()) ? "model" : "user";
          if (!first) body.append(",");
          body.append("{\"role\":\"")
              .append(geminiRole).append("\",")
              .append("\"parts\":[{\"text\":").
              append(escapeJson(m.content())).append("}]}");
          first = false;
      }
      body.append("]}"); // close contents array and root object

      try (OutputStream os = conn.getOutputStream()) {
          os.write(body.toString().getBytes(StandardCharsets.UTF_8));
      }
      int status = conn.getResponseCode();
      if (status != 200) {
          try (InputStream err = conn.getErrorStream()) {
              String errBody = err != null
                  ? new String(err.readAllBytes(), StandardCharsets.UTF_8) : "";
              throw new RuntimeException("Gemini HTTP " + status + ": " + errBody);
          }
      }

      String response;
      try (InputStream is = conn.getInputStream()) {
          response = new String(is.readAllBytes(), StandardCharsets.UTF_8);
      }
            // Parse: candidates[0].content.parts[0].text
      int textIdx = response.indexOf("\"text\":");
      if (textIdx != -1) {
          int start = response.indexOf("\"", textIdx + 7) + 1;
          int end   = response.indexOf("\"", start);
          while (end > 0 && response.charAt(end - 1) == '\\') {
              end = response.indexOf("\"", end + 1);
          }

                log.info("Request Content: "+ messages);
      log.info("Request Proper: "+body);
      log.info("Response: "+ response);
          return response.substring(start, end)
                         .replace("\\n", "\n")
                         .replace("\\\"", "\"")
                         .replace("\\\\", "\\");
      }
      log.info("Request Content: "+ messages);
      log.info("Request Proper: "+body);
      log.info("Response: "+ response);

      return response;

  }

  private String escapeJson(String text) {
    return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\"";
  }
}
