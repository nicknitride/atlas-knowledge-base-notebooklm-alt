package dev.atlas.providers.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class OpenAiDtos {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record OpenAiMessage(String role, String content) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record OpenAiDelta(String role, String content) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record OpenAiChoice(
      Integer index,
      OpenAiMessage message,
      OpenAiDelta delta,
      @JsonProperty("finish_reason") String finishReason
  ) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record OpenAiChatResponse(List<OpenAiChoice> choices) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record OpenAiEmbeddingData(List<Float> embedding) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record OpenAiEmbeddingResponse(List<OpenAiEmbeddingData> data) {}
}
