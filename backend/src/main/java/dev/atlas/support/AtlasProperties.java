package dev.atlas.support;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "atlas")
public class AtlasProperties {
  private long maxUploadBytes = 80L * 1024 * 1024;
  private final Ingestion ingestion = new Ingestion();
  private final Provider provider = new Provider();

  public long getMaxUploadBytes() {
    return maxUploadBytes;
  }

  public void setMaxUploadBytes(long maxUploadBytes) {
    this.maxUploadBytes = maxUploadBytes;
  }

  public Ingestion getIngestion() {
    return ingestion;
  }

  public Provider getProvider() {
    return provider;
  }

  public static class Ingestion {
    private Duration processingTimeout = Duration.ofMinutes(10);

    public Duration getProcessingTimeout() {
      return processingTimeout;
    }

    public void setProcessingTimeout(Duration processingTimeout) {
      this.processingTimeout = processingTimeout;
    }
  }

  public static class Provider {
    private String type = "local";
    private final Ollama ollama = new Ollama();

    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
    }

    public Ollama getOllama() {
      return ollama;
    }

    public boolean isLocalOrOllama() {
      return type == null
          || type.isBlank()
          || "local".equalsIgnoreCase(type)
          || "ollama".equalsIgnoreCase(type);
    }
  }

  public static class Ollama {
    private String url = "http://localhost:11434";
    private String model = "llama3";
    private String embeddingModel = "nomic-embed-text";
    private int embeddingDimensions = 768;

    public String getUrl() {
      return url;
    }

    public void setUrl(String url) {
      this.url = url;
    }

    public String getModel() {
      return model;
    }

    public void setModel(String model) {
      this.model = model;
    }

    public String getEmbeddingModel() {
      return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
      this.embeddingModel = embeddingModel;
    }

    public int getEmbeddingDimensions() {
      return embeddingDimensions;
    }

    public void setEmbeddingDimensions(int embeddingDimensions) {
      this.embeddingDimensions = embeddingDimensions;
    }
  }
}
