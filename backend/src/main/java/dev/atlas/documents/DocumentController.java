package dev.atlas.documents;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import dev.atlas.workspaces.WorkspaceLookup;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/documents")
class DocumentController {
  private static final long MAX_FILE_SIZE = 25 * 1024 * 1024;
  private final KnowledgeDocumentRepository documents;
  private final FileStorage storage;
  private final IngestionService ingestion;
  private final WorkspaceLookup workspaces;
  private final DocumentExtractor extractor;

  DocumentController(KnowledgeDocumentRepository documents, FileStorage storage, IngestionService ingestion, WorkspaceLookup workspaces, DocumentExtractor extractor) {
    this.documents = documents; this.storage = storage; this.ingestion = ingestion; this.workspaces = workspaces; this.extractor = extractor;
  }

  @GetMapping
  List<DocumentResponse> list(@PathVariable UUID workspaceId) {
    workspaces.requireExists(workspaceId);
    return documents.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId).stream().map(DocumentResponse::from).toList();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.ACCEPTED)
  DocumentResponse upload(@PathVariable UUID workspaceId, @RequestParam("file") MultipartFile file) throws IOException {
    workspaces.requireExists(workspaceId);
    if (file.isEmpty() || file.getSize() > MAX_FILE_SIZE) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload a non-empty file smaller than 25 MB");
    String filename = file.getOriginalFilename();
    if (filename == null || filename.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A filename is required");
    String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
    if (!extractor.supports(contentType, filename)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PDF, Markdown, and plain-text documents are supported");
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
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found in workspace"));
    storage.delete(doc.storageKey());
    documents.delete(doc);
  }

  record DocumentResponse(UUID id, String filename, String contentType, IngestionStatus status, String failureReason, Instant createdAt) {
    static DocumentResponse from(KnowledgeDocument document) { return new DocumentResponse(document.id(), document.originalFilename(), document.contentType(), document.ingestionStatus(), document.failureReason(), document.createdAt()); }
  }
}
