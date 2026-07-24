package dev.atlas.providers.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

public class GeminiDtos {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Part(String text) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Content(String role, List<Part> parts) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Candidate(Content content, String finishReason) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record GeminiChatResponse(List<Candidate> candidates) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record EmbeddingObject(List<Float> values) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record GeminiEmbeddingResponse(EmbeddingObject embedding) {}
}
