package dev.atlas.documents;

import dev.atlas.support.ApiException;
import dev.atlas.support.AtlasProperties;
import dev.atlas.workspaces.WorkspaceLookup;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/documents")
class DocumentController {
  private final KnowledgeDocumentRepository documents;
  private final FileStorage storage;
  private final IngestionService ingestion;
  private final WorkspaceLookup workspaces;
  private final DocumentExtractor extractor;
  private final AtlasProperties properties;
  private final JdbcTemplate jdbc;

  DocumentController(
      KnowledgeDocumentRepository documents,
      FileStorage storage,
      IngestionService ingestion,
      WorkspaceLookup workspaces,
      DocumentExtractor extractor,
      AtlasProperties properties,
      JdbcTemplate jdbc) {
    this.documents = documents;
    this.storage = storage;
    this.ingestion = ingestion;
    this.workspaces = workspaces;
    this.extractor = extractor;
    this.properties = properties;
    this.jdbc = jdbc;
  }

  @GetMapping
  List<DocumentResponse> list(@PathVariable UUID workspaceId) {
    workspaces.requireExists(workspaceId);
    return documents.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId).stream()
        .map(DocumentResponse::from)
        .toList();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.ACCEPTED)
  DocumentResponse upload(@PathVariable UUID workspaceId, @RequestParam("file") MultipartFile file)
      throws IOException {
    workspaces.requireExists(workspaceId);
    if (file == null || file.isEmpty()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "UPLOAD_EMPTY", "Upload a non-empty file");
    }
    long maxBytes = properties.getMaxUploadBytes();
    if (file.getSize() > maxBytes) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "UPLOAD_TOO_LARGE",
          "Upload a non-empty file smaller than " + (maxBytes / (1024 * 1024)) + " MB");
    }
    String filename = file.getOriginalFilename();
    if (filename == null || filename.isBlank()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "A filename is required");
    }
    String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
    if (!extractor.supports(contentType, filename)) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "UPLOAD_UNSUPPORTED_TYPE",
          "Only PDF, Markdown, and plain-text documents are supported");
    }
    String key = storage.store(workspaceId, filename, file.getInputStream());
    KnowledgeDocument document = documents.save(new KnowledgeDocument(workspaceId, filename, contentType, key));
    ingestion.ingest(document.id());
    return DocumentResponse.from(document);
  }

  @DeleteMapping("/{documentId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(@PathVariable UUID workspaceId, @PathVariable UUID documentId) {
    workspaces.requireExists(workspaceId);
    KnowledgeDocument doc = documents.findByIdAndWorkspaceId(documentId, workspaceId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Document not found in workspace"));
    ingestion.cancelJobsForDocument(documentId);
    storage.delete(doc.storageKey());
    documents.delete(doc);
    Long remainingComplete = jdbc.queryForObject(
        "SELECT COUNT(*) FROM documents WHERE workspace_id = ? AND ingestion_status = 'COMPLETE'",
        Long.class,
        workspaceId);
    if (remainingComplete != null && remainingComplete == 0L) {
      workspaces.clearEmbeddingIdentity(workspaceId);
    }
  }

  record DocumentResponse(
      UUID id,
      String filename,
      String contentType,
      IngestionStatus status,
      String failureReason,
      Instant createdAt) {
    static DocumentResponse from(KnowledgeDocument document) {
      return new DocumentResponse(
          document.id(),
          document.originalFilename(),
          document.contentType(),
          document.ingestionStatus(),
          document.failureReason(),
          document.createdAt());
    }
  }
}
