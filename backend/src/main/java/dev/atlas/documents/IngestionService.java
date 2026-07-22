package dev.atlas.documents;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class IngestionService {
  private final KnowledgeDocumentRepository documents;
  private final FileStorage storage;
  private final DocumentExtractor extractor;
  private final JdbcTemplate jdbc;
  IngestionService(KnowledgeDocumentRepository documents, FileStorage storage, DocumentExtractor extractor, JdbcTemplate jdbc) {
    this.documents = documents; this.storage = storage; this.extractor = extractor; this.jdbc = jdbc;
  }
  @Async
  @Transactional
  public void ingest(UUID documentId) {
    KnowledgeDocument document = documents.findById(documentId).orElse(null);
    if (document == null) return;
    try {
      document.markProcessing();
      List<DocumentExtractor.ExtractedSection> sections = extractor.extract(storage.resolve(document.storageKey()), document.contentType(), document.originalFilename());
      int ordinal = 0;
      for (DocumentExtractor.ExtractedSection section : sections) {
        for (String chunk : chunk(section.content())) {
          jdbc.update("INSERT INTO document_chunks (document_id, ordinal, content, source_locator) VALUES (?, ?, ?, CAST(? AS jsonb))",
              document.id(), ordinal++, chunk, "{\"location\":\"" + section.locator() + "\"}");
        }
      }
      if (ordinal == 0) throw new IllegalArgumentException("No readable text was found in this document");
      document.markComplete();
    } catch (Exception exception) {
      document.markFailed("The document could not be processed");
    }
  }
  private List<String> chunk(String text) {
    java.util.ArrayList<String> chunks = new java.util.ArrayList<>();
    String normalized = text.replaceAll("\\s+", " ").trim();
    int start = 0;
    while (start < normalized.length()) {
      int end = Math.min(normalized.length(), start + 1100);
      if (end < normalized.length()) { int boundary = normalized.lastIndexOf(' ', end); if (boundary > start + 300) end = boundary; }
      chunks.add(normalized.substring(start, end));
      if (end == normalized.length()) break;
      start = end - 150;
    }
    return chunks;
  }
}
