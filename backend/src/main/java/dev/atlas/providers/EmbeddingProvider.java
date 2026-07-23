package dev.atlas.providers;

import java.util.List;

public interface EmbeddingProvider {
  float[] embed(String text);
  List<float[]> embedAll(List<String> texts);
}
