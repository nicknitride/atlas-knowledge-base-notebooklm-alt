package dev.atlas.providers;

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

  @Value("${atlas.provider.type:local}")
  private String providerType;

  @Value("${atlas.provider.ollama.url:http://localhost:11434}")
  private String ollamaUrl;

  @Value("${atlas.provider.openai.api-key:}")
  private String openAiApiKey;

  @Override
  public float[] embed(String text) {
    if ("ollama".equalsIgnoreCase(providerType)) {
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

  private float[] embedOllama(String text) throws Exception {
    URI uri = URI.create(ollamaUrl + "/api/embeddings");
    HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
    conn.setRequestMethod("POST");
    conn.setDoOutput(true);
    conn.setRequestProperty("Content-Type", "application/json");
    String payload = String.format("{\"model\":\"nomic-embed-text\",\"prompt\":%s}", escapeJson(text));
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
    return parseEmbeddingFromJson(response);
  }

  private float[] embedOpenAi(String text) throws Exception {
    URI uri = URI.create("https://api.openai.com/v1/embeddings");
    HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
    conn.setRequestMethod("POST");
    conn.setDoOutput(true);
    conn.setRequestProperty("Content-Type", "application/json");
    conn.setRequestProperty("Authorization", "Bearer " + openAiApiKey);
    String payload = String.format("{\"model\":\"text-embedding-3-small\",\"input\":%s}", escapeJson(text));
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
    return parseEmbeddingFromJson(response);
  }

  private float[] parseEmbeddingFromJson(String json) {
    int idx = json.indexOf("\"embedding\":[");
    if (idx == -1) return embedDeterministic(json);
    int start = idx + "\"embedding\":[".length();
    int end = json.indexOf("]", start);
    String arrStr = json.substring(start, end);
    String[] parts = arrStr.split(",");
    float[] vector = new float[Math.min(parts.length, DIMENSION)];
    for (int i = 0; i < vector.length; i++) {
      vector[i] = Float.parseFloat(parts[i].trim());
    }
    return vector;
  }

  private String escapeJson(String text) {
    return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\"";
  }
}
