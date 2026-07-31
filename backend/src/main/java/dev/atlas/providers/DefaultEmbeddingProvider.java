package dev.atlas.providers;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.atlas.providers.dto.OllamaDtos.OllamaEmbeddingResponse;
import dev.atlas.providers.dto.OpenAiDtos.OpenAiEmbeddingResponse;
import dev.atlas.support.ApiException;
import dev.atlas.support.AtlasProperties;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class DefaultEmbeddingProvider implements EmbeddingProvider {
  private static final Logger log = LoggerFactory.getLogger(DefaultEmbeddingProvider.class);

  private final ObjectMapper objectMapper;
  private final AtlasProperties properties;

  @Value("${atlas.provider.openai.api-key:}")
  private String openAiApiKey;

  public DefaultEmbeddingProvider(ObjectMapper objectMapper, AtlasProperties properties) {
    this.objectMapper = objectMapper;
    this.properties = properties;
  }

  @Override
  public String embeddingModelName() {
    return properties.getProvider().getOllama().getEmbeddingModel();
  }

  @Override
  public int embeddingDimensions() {
    return properties.getProvider().getOllama().getEmbeddingDimensions();
  }

  @Override
  public float[] embed(String text) {
    String type = properties.getProvider().getType();
    try {
      if (properties.getProvider().isLocalOrOllama()) {
        return embedOllama(text);
      }
      if ("openai".equalsIgnoreCase(type)) {
        return embedOpenAi(text);
      }
      if ("gemini".equalsIgnoreCase(type)) {
        // Gemini chat may be configured; embeddings for this feature use Ollama-compatible local path
        // unless OpenAI is selected. Prefer Ollama embeddings when gemini is chat-only.
        return embedOllama(text);
      }
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "PROVIDER_MISCONFIGURED",
          "Unsupported embedding provider type: " + type);
    } catch (ApiException ex) {
      throw ex;
    } catch (Exception e) {
      log.warn("Embedding provider failed: {}", e.getMessage());
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "PROVIDER_UNAVAILABLE",
          "Embedding backend is unavailable. Check the local AI endpoint and embedding model.");
    }
  }

  @Override
  public List<float[]> embedAll(List<String> texts) {
    List<float[]> embeddings = new ArrayList<>();
    for (String text : texts) {
      embeddings.add(embed(text));
    }
    return embeddings;
  }

  private float[] embedOllama(String text) throws Exception {
    String ollamaUrl = properties.getProvider().getOllama().getUrl();
    String model = properties.getProvider().getOllama().getEmbeddingModel();
    int expectedDims = embeddingDimensions();

    URI uri = URI.create(ollamaUrl + "/api/embeddings");
    HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
    conn.setConnectTimeout(10_000);
    conn.setReadTimeout(60_000);
    conn.setRequestMethod("POST");
    conn.setDoOutput(true);
    conn.setRequestProperty("Content-Type", "application/json");

    String payload = objectMapper.writeValueAsString(java.util.Map.of(
        "model", model,
        "prompt", text));

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
    if (dto == null || dto.embedding() == null || dto.embedding().isEmpty()) {
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "PROVIDER_MISCONFIGURED",
          "Embedding model returned an empty vector. Is '" + model + "' pulled in Ollama?");
    }
    float[] vector = toFloatArray(dto.embedding());
    if (vector.length != expectedDims) {
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "PROVIDER_MISCONFIGURED",
          "Embedding model returned " + vector.length + " dimensions but atlas.provider.ollama.embedding-dimensions is "
              + expectedDims);
    }
    return vector;
  }

  private float[] embedOpenAi(String text) throws Exception {
    if (openAiApiKey == null || openAiApiKey.isBlank()) {
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "PROVIDER_MISCONFIGURED",
          "OpenAI API key is not configured");
    }
    URI uri = URI.create("https://api.openai.com/v1/embeddings");
    HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
    conn.setConnectTimeout(10_000);
    conn.setReadTimeout(60_000);
    conn.setRequestMethod("POST");
    conn.setDoOutput(true);
    conn.setRequestProperty("Content-Type", "application/json");
    conn.setRequestProperty("Authorization", "Bearer " + openAiApiKey);

    String payload = objectMapper.writeValueAsString(java.util.Map.of(
        "model", "text-embedding-3-small",
        "input", text));

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
    if (dto == null || dto.data() == null || dto.data().isEmpty() || dto.data().get(0).embedding() == null) {
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "PROVIDER_UNAVAILABLE",
          "OpenAI embedding response was empty");
    }
    return toFloatArray(dto.data().get(0).embedding());
  }

  private float[] toFloatArray(List<Float> list) {
    float[] vector = new float[list.size()];
    for (int i = 0; i < vector.length; i++) {
      vector[i] = list.get(i);
    }
    return vector;
  }
}
