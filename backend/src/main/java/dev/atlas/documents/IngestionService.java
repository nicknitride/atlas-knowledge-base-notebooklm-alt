package dev.atlas.documents;

import dev.atlas.providers.EmbeddingProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IngestionService {
  private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

  private final KnowledgeDocumentRepository documents;
  private final FileStorage storage;
  private final DocumentExtractor extractor;
  private final EmbeddingProvider embeddingProvider;
  private final JdbcTemplate jdbc;

  public IngestionService(KnowledgeDocumentRepository documents, FileStorage storage, DocumentExtractor extractor, EmbeddingProvider embeddingProvider, JdbcTemplate jdbc) {
    this.documents = documents;
    this.storage = storage;
    this.extractor = extractor;
    this.embeddingProvider = embeddingProvider;
    this.jdbc = jdbc;
  }

  @Async
  @Transactional
  public void ingest(UUID documentId) {
    KnowledgeDocument document = documents.findById(documentId).orElse(null);
    if (document == null) return;
    try {
      document.markProcessing();
      documents.saveAndFlush(document);

      List<DocumentExtractor.ExtractedSection> sections = extractor.extract(
          storage.resolve(document.storageKey()),
          document.contentType(),
          document.originalFilename()
      );

      int ordinal = 0;
      for (DocumentExtractor.ExtractedSection section : sections) {
        for (String chunkText : chunk(section.content())) {
          float[] embedding = embeddingProvider.embed(chunkText);
          String vectorStr = toVectorString(embedding);
          String sourceLocatorJson = String.format("{\"location\":\"%s\",\"filename\":\"%s\"}", 
              escapeJson(section.locator()), 
              escapeJson(document.originalFilename()));

          jdbc.update(
              "INSERT INTO document_chunks (document_id, ordinal, content, source_locator, embedding) VALUES (?, ?, ?, CAST(? AS jsonb), CAST(? AS vector))",
              document.id(), ordinal++, chunkText, sourceLocatorJson, vectorStr
          );
        }
      }

      if (ordinal == 0) {
        throw new IllegalArgumentException("No readable text was found in this document");
      }

      document.markComplete();
      documents.saveAndFlush(document);
      log.info("Document {} ingested successfully with {} chunks", documentId, ordinal);
    } catch (Exception exception) {
      log.error("Failed to ingest document {}", documentId, exception);
      document.markFailed("The document could not be processed: " + exception.getMessage());
      documents.saveAndFlush(document);
    }
  }

  private String toVectorString(float[] vector) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < vector.length; i++) {
      sb.append(vector[i]);
      if (i < vector.length - 1) sb.append(",");
    }
    sb.append("]");
    return sb.toString();
  }

  private String escapeJson(String input) {
    if (input == null) return "";
    return input.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
  }

  private List<String> chunk(String text) {
    List<String> chunks = new ArrayList<>();
    String normalized = text.replaceAll("\\s+", " ").trim();
    int start = 0;
    while (start < normalized.length()) {
      int end = Math.min(normalized.length(), start + 1100);
      if (end < normalized.length()) {
        int boundary = normalized.lastIndexOf(' ', end);
        if (boundary > start + 300) end = boundary;
      }
      chunks.add(normalized.substring(start, end));
      if (end == normalized.length()) break;
      start = end - 150;
    }
    return chunks;
  }
}
