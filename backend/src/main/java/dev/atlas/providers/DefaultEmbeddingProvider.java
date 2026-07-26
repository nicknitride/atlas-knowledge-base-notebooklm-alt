package dev.atlas.providers;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.atlas.providers.dto.GeminiDtos.GeminiEmbeddingResponse;
import dev.atlas.providers.dto.OllamaDtos.OllamaEmbeddingResponse;
import dev.atlas.providers.dto.OpenAiDtos.OpenAiEmbeddingResponse;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DefaultEmbeddingProvider implements EmbeddingProvider {
  private static final Logger log = LoggerFactory.getLogger(DefaultEmbeddingProvider.class);
  private static final int DIMENSION = 1536;

  private final ObjectMapper objectMapper;

  @Value("${atlas.provider.type:local}")
  private String providerType;

  @Value("${atlas.provider.ollama.url:http://localhost:11434}")
  private String ollamaUrl;

  @Value("${atlas.provider.openai.api-key:}")
  private String openAiApiKey;

  @Value("${atlas.provider.gemini.api-key:}")
  private String geminiApiKey;

  @Value("${atlas.provider.gemini.embedding-model-gemini:text-embedding-004}")
  private String geminiEmbeddingProvider;

  public DefaultEmbeddingProvider(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public float[] embed(String text) {
    if ("ollama".equalsIgnoreCase(providerType) || "gemini".equalsIgnoreCase(providerType)) {
      try {
        return embedOllama(text);
      } catch (Exception e) {
        log.warn("Ollama embedding failed, falling back to deterministic local embeddings: {}", e.getMessage());
      }
    } else if ("openai".equalsIgnoreCase(providerType) && !openAiApiKey.isBlank()) {
      try {
        return embedOpenAi(text);
      } catch (Exception e) {
        log.warn("OpenAI embedding failed, falling back to deterministic local embeddings: {}", e.getMessage());
      }
    }
    return embedDeterministic(text);
  }

  @Override
  public List<float[]> embedAll(List<String> texts) {
    List<float[]> embeddings = new ArrayList<>();
    for (String text : texts) {
      embeddings.add(embed(text));
    }
    return embeddings;
  }
//TODO: decide if I'm keeping this
  private float[] embedDeterministic(String text) {
    float[] vector = new float[DIMENSION];
    try {
      byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
      for (int i = 0; i < DIMENSION; i++) {
        int byteIndex = i % hash.length;
        int val = (hash[byteIndex] & 0xFF) ^ ((i * 31) & 0xFF);
        vector[i] = (float) (val - 128) / 128.0f;
      }
      float norm = 0;
      for (float v : vector) norm += v * v;
      norm = (float) Math.sqrt(norm);
      if (norm > 0) {
        for (int i = 0; i < DIMENSION; i++) vector[i] /= norm;
      }
    } catch (Exception e) {
      log.error("Embedding generation error", e);
    }
    return vector;
  }

  //TODO add an embedding model
  private float[] embedOllama(String text) throws Exception {
    URI uri = URI.create(ollamaUrl + "/api/embeddings");
    HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
    conn.setRequestMethod("POST");
    conn.setDoOutput(true);
    conn.setRequestProperty("Content-Type", "application/json");

    String payload = objectMapper.writeValueAsString(java.util.Map.of(
        "model", "nomic-embed-text",
        "prompt", text
    ));

    try (OutputStream os = conn.getOutputStream()) {
      os.write(payload.getBytes(StandardCharsets.UTF_8));
    }
    if (conn.getResponseCode() != 200) {
      throw new RuntimeException("Ollama returned status " + conn.getResponseCode());
    }
    String response;
    try (InputStream is = conn.getInputStream()) {
      response = new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }
    OllamaEmbeddingResponse dto = objectMapper.readValue(response, OllamaEmbeddingResponse.class);
    if (dto != null && dto.embedding() != null && !dto.embedding().isEmpty()) {
      return toFloatArray(dto.embedding());
    }
    return embedDeterministic(text);
  }

  private float[] embedOpenAi(String text) throws Exception {
    URI uri = URI.create("https://api.openai.com/v1/embeddings");
    HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
    conn.setRequestMethod("POST");
    conn.setDoOutput(true);
    conn.setRequestProperty("Content-Type", "application/json");
    conn.setRequestProperty("Authorization", "Bearer " + openAiApiKey);

    String payload = objectMapper.writeValueAsString(java.util.Map.of(
        "model", "text-embedding-3-small",
        "input", text
    ));

    try (OutputStream os = conn.getOutputStream()) {
      os.write(payload.getBytes(StandardCharsets.UTF_8));
    }
    if (conn.getResponseCode() != 200) {
      throw new RuntimeException("OpenAI returned status " + conn.getResponseCode());
    }
    String response;
    try (InputStream is = conn.getInputStream()) {
      response = new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }
    OpenAiEmbeddingResponse dto = objectMapper.readValue(response, OpenAiEmbeddingResponse.class);
    if (dto != null && dto.data() != null && !dto.data().isEmpty() && dto.data().get(0).embedding() != null) {
      return toFloatArray(dto.data().get(0).embedding());
    }
    return embedDeterministic(text);
  }

  private float[] toFloatArray(List<Float> list) {
    float[] vector = new float[Math.min(list.size(), DIMENSION)];
    for (int i = 0; i < vector.length; i++) {
      vector[i] = list.get(i);
    }
    return vector;
  }
}
