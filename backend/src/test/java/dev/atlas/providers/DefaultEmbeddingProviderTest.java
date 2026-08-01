package dev.atlas.providers;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.atlas.support.ApiException;
import dev.atlas.support.AtlasProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultEmbeddingProviderTest {
  private AtlasProperties properties;
  private DefaultEmbeddingProvider provider;

  @BeforeEach
  void setUp() {
    properties = new AtlasProperties();
    properties.getProvider().setType("local");
    properties.getProvider().getOllama().setUrl("http://127.0.0.1:1");
    properties.getProvider().getOllama().setEmbeddingModel("nomic-embed-text");
    properties.getProvider().getOllama().setEmbeddingDimensions(768);
    provider = new DefaultEmbeddingProvider(new ObjectMapper(), properties);
  }

  @Test
  void failsClosedWhenOllamaUnavailable() {
    ApiException ex = assertThrows(ApiException.class, () -> provider.embed("Sample text"));
    assertEquals("PROVIDER_UNAVAILABLE", ex.code());
  }

  @Test
  void reportsConfiguredEmbeddingModelAndDimensions() {
    assertEquals("nomic-embed-text", provider.embeddingModelName());
    assertEquals(768, provider.embeddingDimensions());
  }
}
