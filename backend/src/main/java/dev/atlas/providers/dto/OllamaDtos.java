package dev.atlas.providers.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

public class OllamaDtos {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record OllamaChatMessage(String role, String content, String thinking) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record OllamaChatResponse(OllamaChatMessage message, Boolean done) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record OllamaEmbeddingResponse(List<Float> embedding) {}
}
