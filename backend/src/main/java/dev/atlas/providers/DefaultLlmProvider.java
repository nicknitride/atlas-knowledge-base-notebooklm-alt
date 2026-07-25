package dev.atlas.providers;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.atlas.providers.dto.GeminiDtos;
import dev.atlas.providers.dto.OllamaDtos;
import dev.atlas.providers.dto.OpenAiDtos;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import java.util.concurrent.TimeUnit;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.netty.http.client.HttpClient;

@Service
public class DefaultLlmProvider implements LlmProvider {
  private static final Logger log = LoggerFactory.getLogger(DefaultLlmProvider.class);

  private final ObjectMapper objectMapper;
  private final WebClient webClient;

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

  @Value("${atlas.provider.gemini.model:gemini-1.5-flash}")
  private String geminiModel;

  public DefaultLlmProvider(ObjectMapper objectMapper, WebClient.Builder webClientBuilder) {
    this.objectMapper = objectMapper;
    HttpClient httpClient = HttpClient.create()
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30000)
        .responseTimeout(Duration.ofSeconds(60))
        .doOnConnected(conn -> conn
            .addHandlerLast(new ReadTimeoutHandler(60, TimeUnit.SECONDS))
            .addHandlerLast(new WriteTimeoutHandler(60, TimeUnit.SECONDS)));

    this.webClient = webClientBuilder
        .clientConnector(new ReactorClientHttpConnector(httpClient))
        .build();
  }

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
      }
    } else if ("gemini".equalsIgnoreCase(providerType) && !geminiApiKey.isBlank()) {
      try {
        return generateGemini(messages);
      } catch (Exception e) {
        log.warn("Gemini call failed: {}", e.getMessage());
      }
    }
    return generateFallback(messages);
  }

  @Override
  public void stream(List<ChatMessage> messages, Consumer<String> chunkConsumer, Runnable onComplete, Consumer<Throwable> onError) {
    log.info("LlmProvider.stream invoked - providerType: '{}', geminiApiKeyPresent: {}, model: '{}'", providerType, !geminiApiKey.isBlank(), geminiModel);
    if ("ollama".equalsIgnoreCase(providerType)) {
      streamOllama(messages, chunkConsumer, onComplete, onError);
    } else if ("openai".equalsIgnoreCase(providerType) && !openAiApiKey.isBlank()) {
      streamOpenAi(messages, chunkConsumer, onComplete, onError);
    } else if ("gemini".equalsIgnoreCase(providerType) && !geminiApiKey.isBlank()) {
      streamGemini(messages, chunkConsumer, onComplete, onError);
    } else {
      log.warn("No valid active LLM provider configured (providerType='{}', keyPresent={}). Executing offline fallback.", providerType, !geminiApiKey.isBlank());
      streamFallback(messages, chunkConsumer, onComplete, onError);
    }
  }

  private void streamOllama(List<ChatMessage> messages, Consumer<String> chunkConsumer, Runnable onComplete, Consumer<Throwable> onError) {
    Map<String, Object> body = Map.of(
        "model", ollamaModel,
        "stream", true,
        "messages", messages.stream().map(m -> Map.of("role", m.role(), "content", m.content())).toList()
    );

    webClient.post()
        .uri(ollamaUrl + "/api/chat")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(body)
        .retrieve()
        .bodyToFlux(String.class)
        .subscribe(
            line -> {
              try {
                OllamaDtos.OllamaChatResponse chunk = objectMapper.readValue(line, OllamaDtos.OllamaChatResponse.class);
                if (chunk != null && chunk.message() != null && chunk.message().content() != null) {
                  chunkConsumer.accept(chunk.message().content());
                }
              } catch (Exception e) {
                log.trace("Error parsing Ollama stream chunk line: {}", line, e);
              }
            },
            error -> {
              log.warn("Ollama streaming error, falling back: {}", error.getMessage());
              streamFallback(messages, chunkConsumer, onComplete, onError);
            },
            onComplete::run
        );
  }

  private void streamOpenAi(List<ChatMessage> messages, Consumer<String> chunkConsumer, Runnable onComplete, Consumer<Throwable> onError) {
    Map<String, Object> body = Map.of(
        "model", openAiModel,
        "stream", true,
        "messages", messages.stream().map(m -> Map.of("role", m.role(), "content", m.content())).toList()
    );

    webClient.post()
        .uri("https://api.openai.com/v1/chat/completions")
        .header("Authorization", "Bearer " + openAiApiKey)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(body)
        .retrieve()
        .bodyToFlux(String.class)
        .subscribe(
            line -> {
              String trimmed = line.trim();
              if (trimmed.startsWith("data: ")) {
                String json = trimmed.substring(6).trim();
                if ("[DONE]".equalsIgnoreCase(json)) return;
                try {
                  OpenAiDtos.OpenAiChatResponse chunk = objectMapper.readValue(json, OpenAiDtos.OpenAiChatResponse.class);
                  if (chunk != null && chunk.choices() != null && !chunk.choices().isEmpty()) {
                    OpenAiDtos.OpenAiDelta delta = chunk.choices().get(0).delta();
                    if (delta != null && delta.content() != null) {
                      chunkConsumer.accept(delta.content());
                    }
                  }
                } catch (Exception e) {
                  log.trace("Error parsing OpenAI stream chunk", e);
                }
              }
            },
            error -> {
              log.warn("OpenAI streaming error, falling back: {}", error.getMessage());
              streamFallback(messages, chunkConsumer, onComplete, onError);
            },
            onComplete::run
        );
  }

  private void streamGemini(List<ChatMessage> messages, Consumer<String> chunkConsumer, Runnable onComplete, Consumer<Throwable> onError) {
    Map<String, Object> body = buildGeminiRequestBody(messages);

    if (body == null) {
      log.warn("Gemini request body was empty (no valid content messages). Invoking fallback.");
      streamFallback(messages, chunkConsumer, onComplete, onError);
      return;
    }

    String url = "https://generativelanguage.googleapis.com/v1beta/models/" + geminiModel + ":streamGenerateContent?key=" + geminiApiKey + "&alt=sse";
    AtomicBoolean emittedAny = new AtomicBoolean(false);

    webClient.post()
        .uri(url)
        .header("Accept", "text/event-stream")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(body)
        .retrieve()
        .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
        .timeout(Duration.ofSeconds(45))
        .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
            .maxBackoff(Duration.ofSeconds(4))
            .jitter(0.5)
            .filter(ex -> ex instanceof WebClientResponseException wce
                && (wce.getStatusCode().value() == 429 || wce.getStatusCode().value() == 503))
            .doBeforeRetry(signal -> log.info("Retrying Gemini stream after {} (attempt {})",
                signal.failure().getMessage(), signal.totalRetries() + 1)))
        .subscribe(
            sse -> {
              String json = sse.data();
              if (json == null || json.isBlank() || "[DONE]".equalsIgnoreCase(json.trim())) {
                return;
              }
              try {
                GeminiDtos.GeminiChatResponse response = objectMapper.readValue(json, GeminiDtos.GeminiChatResponse.class);
                if (response != null && response.candidates() != null && !response.candidates().isEmpty()) {
                  GeminiDtos.Candidate candidate = response.candidates().get(0);
                  if (candidate.content() != null && candidate.content().parts() != null) {
                    for (GeminiDtos.Part p : candidate.content().parts()) {
                      if (p.text() != null && !p.text().isEmpty()) {
                        emittedAny.set(true);
                        chunkConsumer.accept(p.text());
                      }
                    }
                  } else if (candidate.finishReason() != null && !"STOP".equalsIgnoreCase(candidate.finishReason())) {
                    emittedAny.set(true);
                    chunkConsumer.accept("Response generated was halted by provider (" + candidate.finishReason() + ").");
                  }
                }
              } catch (Exception e) {
                log.warn("Error parsing Gemini SSE event data: {} (raw: {})", e.getMessage(), json);
              }
            },
            error -> {
              log.warn("Gemini streaming error, falling back: {}", error.getMessage());
              streamFallback(messages, chunkConsumer, onComplete, onError);
            },
            () -> {
              if (!emittedAny.get()) {
                log.warn("Gemini stream completed without producing text, invoking fallback synthesis.");
                streamFallback(messages, chunkConsumer, onComplete, onError);
              } else {
                onComplete.run();
              }
            }
        );
  }

  private void streamFallback(List<ChatMessage> messages, Consumer<String> chunkConsumer, Runnable onComplete, Consumer<Throwable> onError) {
    try {
      String fullResponse = generateFallback(messages);
      chunkConsumer.accept(fullResponse);
      onComplete.run();
    } catch (Exception e) {
      onError.accept(e);
    }
  }

  private String generateOllama(List<ChatMessage> messages) throws Exception {
    Map<String, Object> body = Map.of(
        "model", ollamaModel,
        "stream", false,
        "messages", messages.stream().map(m -> Map.of("role", m.role(), "content", m.content())).toList()
    );

    String jsonResponse = webClient.post()
        .uri(ollamaUrl + "/api/chat")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(body)
        .retrieve()
        .bodyToMono(String.class)
        .block(Duration.ofSeconds(60));

    OllamaDtos.OllamaChatResponse res = objectMapper.readValue(jsonResponse, OllamaDtos.OllamaChatResponse.class);
    log.info(res.toString());
    if (res != null && res.message() != null && res.message().content() != null) {
      return res.message().content();
    }
    log.info(jsonResponse);
    return jsonResponse;
  }

  private String generateOpenAi(List<ChatMessage> messages) throws Exception {
    Map<String, Object> body = Map.of(
        "model", openAiModel,
        "messages", messages.stream().map(m -> Map.of("role", m.role(), "content", m.content())).toList()
    );

    String jsonResponse = webClient.post()
        .uri("https://api.openai.com/v1/chat/completions")
        .header("Authorization", "Bearer " + openAiApiKey)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(body)
        .retrieve()
        .bodyToMono(String.class)
        .block(Duration.ofSeconds(60));

    OpenAiDtos.OpenAiChatResponse res = objectMapper.readValue(jsonResponse, OpenAiDtos.OpenAiChatResponse.class);
    if (res != null && res.choices() != null && !res.choices().isEmpty() && res.choices().get(0).message() != null) {
      return res.choices().get(0).message().content();
    }
    return jsonResponse;
  }

  private String generateGemini(List<ChatMessage> messages) throws Exception {
    Map<String, Object> body = buildGeminiRequestBody(messages);

    String url = "https://generativelanguage.googleapis.com/v1beta/models/" + geminiModel + ":generateContent?key=" + geminiApiKey;
    String jsonResponse = webClient.post()
        .uri(url)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(body)
        .retrieve()
        .bodyToMono(String.class)
        .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
            .maxBackoff(Duration.ofSeconds(4))
            .jitter(0.5)
            .filter(ex -> ex instanceof WebClientResponseException wce
                && (wce.getStatusCode().value() == 429 || wce.getStatusCode().value() == 503))
            .doBeforeRetry(signal -> log.info("Retrying Gemini generate after {} (attempt {})",
                signal.failure().getMessage(), signal.totalRetries() + 1)))
        .block(Duration.ofSeconds(60));

    GeminiDtos.GeminiChatResponse res = objectMapper.readValue(jsonResponse, GeminiDtos.GeminiChatResponse.class);
    if (res != null && res.candidates() != null && !res.candidates().isEmpty()) {
      GeminiDtos.Candidate c = res.candidates().get(0);
      if (c.content() != null && c.content().parts() != null && !c.content().parts().isEmpty()) {
        return c.content().parts().get(0).text();
      }
    }
    return jsonResponse;
  }

  private Map<String, Object> buildGeminiRequestBody(List<ChatMessage> messages) {
    String systemText = null;
    List<Map<String, Object>> contents = new ArrayList<>();
    String lastRole = null;

    for (ChatMessage m : messages) {
      if ("system".equalsIgnoreCase(m.role())) {
        systemText = m.content();
      } else if (m.content() != null && !m.content().isBlank()) {
        String role = "assistant".equalsIgnoreCase(m.role()) ? "model" : "user";
        if (role.equals(lastRole) && !contents.isEmpty()) {
          // Merge into the previous content entry's parts list instead of dropping
          Map<String, Object> prev = contents.get(contents.size() - 1);
          @SuppressWarnings("unchecked")
          List<Map<String, Object>> existingParts = (List<Map<String, Object>>) prev.get("parts");
          List<Map<String, Object>> mergedParts = new ArrayList<>(existingParts);
          mergedParts.add(Map.of("text", m.content().trim()));
          Map<String, Object> merged = new HashMap<>();
          merged.put("role", role);
          merged.put("parts", mergedParts);
          contents.set(contents.size() - 1, merged);
        } else {
          Map<String, Object> entry = new HashMap<>();
          entry.put("role", role);
          entry.put("parts", new ArrayList<>(List.of(Map.of("text", m.content().trim()))));
          contents.add(entry);
          lastRole = role;
        }
      }
    }

    if (contents.isEmpty()) {
      log.error("buildGeminiRequestBody produced empty contents from {} message(s); falling back.", messages.size());
      return null;
    }

    if (systemText != null && !systemText.isBlank()) {
      Map<String, Object> body = new HashMap<>();
      body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", systemText))));
      body.put("contents", contents);
      return body;
    }
    return Map.of("contents", contents);
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
}
