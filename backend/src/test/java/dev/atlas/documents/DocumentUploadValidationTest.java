package dev.atlas.documents;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.atlas.support.ApiException;
import dev.atlas.support.AtlasProperties;
import dev.atlas.workspaces.WorkspaceLookup;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class DocumentUploadValidationTest {
  private DocumentController controller;
  private DocumentExtractor extractor;

  @BeforeEach
  void setUp() {
    KnowledgeDocumentRepository documents = mock(KnowledgeDocumentRepository.class);
    FileStorage storage = mock(FileStorage.class);
    IngestionService ingestion = mock(IngestionService.class);
    WorkspaceLookup workspaces = mock(WorkspaceLookup.class);
    extractor = mock(DocumentExtractor.class);
    AtlasProperties properties = new AtlasProperties();
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    controller = new DocumentController(documents, storage, ingestion, workspaces, extractor, properties, jdbc);
  }

  @Test
  void rejectsEmptyUpload() {
    MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", new byte[0]);
    ApiException ex = assertThrows(ApiException.class, () -> controller.upload(java.util.UUID.randomUUID(), file));
    assertEquals("UPLOAD_EMPTY", ex.code());
    assertEquals(HttpStatus.BAD_REQUEST, ex.status());
  }

  @Test
  void rejectsOversizeUpload() {
    MultipartFile file = mock(MultipartFile.class);
    when(file.isEmpty()).thenReturn(false);
    when(file.getSize()).thenReturn(80L * 1024 * 1024 + 1);
    when(file.getOriginalFilename()).thenReturn("big.txt");
    when(file.getContentType()).thenReturn("text/plain");
    ApiException ex = assertThrows(ApiException.class, () -> controller.upload(java.util.UUID.randomUUID(), file));
    assertEquals("UPLOAD_TOO_LARGE", ex.code());
  }

  @Test
  void rejectsUnsupportedType() {
    MockMultipartFile file = new MockMultipartFile("file", "a.exe", "application/octet-stream", "x".getBytes());
    when(extractor.supports(anyString(), anyString())).thenReturn(false);
    ApiException ex = assertThrows(ApiException.class, () -> controller.upload(java.util.UUID.randomUUID(), file));
    assertEquals("UPLOAD_UNSUPPORTED_TYPE", ex.code());
  }
}
