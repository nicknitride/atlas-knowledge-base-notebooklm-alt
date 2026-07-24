package dev.atlas.providers;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultEmbeddingProviderTest {
  private DefaultEmbeddingProvider provider;

  @BeforeEach
  void setUp() {
    ObjectMapper mapper = new ObjectMapper();
    provider = new DefaultEmbeddingProvider(mapper);
  }

  @Test
  void testFallbackDeterministicEmbeddingReturnsExpectedDimension() {
    float[] vector = provider.embed("Sample text for vector generation");
    assertNotNull(vector);
    assertEquals(1536, vector.length);
  }
}
