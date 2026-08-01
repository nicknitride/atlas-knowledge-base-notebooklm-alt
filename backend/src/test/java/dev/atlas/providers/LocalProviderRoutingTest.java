package dev.atlas.providers;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.atlas.support.ApiException;
import dev.atlas.support.AtlasProperties;
import org.junit.jupiter.api.Test;

class LocalProviderRoutingTest {
  @Test
  void localProviderTypeUsesOllamaEmbeddingPathNotHashVectors() {
    AtlasProperties properties = new AtlasProperties();
    properties.getProvider().setType("local");
    properties.getProvider().getOllama().setUrl("http://127.0.0.1:1");
    DefaultEmbeddingProvider provider = new DefaultEmbeddingProvider(new ObjectMapper(), properties);

    ApiException ex = assertThrows(ApiException.class, () -> provider.embed("hello"));
    assertEquals("PROVIDER_UNAVAILABLE", ex.code());
    // Hash fallback would have returned a vector without throwing
  }
}
