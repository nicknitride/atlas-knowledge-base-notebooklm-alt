package dev.atlas.documents;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DocumentHealthStatusTest {

  @Test
  void testCalculateReadyWhenMatchingActiveConfig() {
    DocumentHealthStatus status = DocumentHealthStatus.calculate(
        IngestionStatus.COMPLETE, "nomic-embed-text", 768, "nomic-embed-text", 768);
    assertEquals(DocumentHealthStatus.READY, status);
  }

  @Test
  void testCalculateStaleWhenModelMismatched() {
    DocumentHealthStatus status = DocumentHealthStatus.calculate(
        IngestionStatus.COMPLETE, "nomic-embed-text", 768, "mxbai-embed-large", 768);
    assertEquals(DocumentHealthStatus.STALE, status);
  }

  @Test
  void testCalculateStaleWhenDimensionsMismatched() {
    DocumentHealthStatus status = DocumentHealthStatus.calculate(
        IngestionStatus.COMPLETE, "nomic-embed-text", 768, "nomic-embed-text", 1024);
    assertEquals(DocumentHealthStatus.STALE, status);
  }

  @Test
  void testCalculatePendingWhenProcessing() {
    DocumentHealthStatus status = DocumentHealthStatus.calculate(
        IngestionStatus.PROCESSING, "nomic-embed-text", 768, "nomic-embed-text", 768);
    assertEquals(DocumentHealthStatus.PENDING, status);
  }

  @Test
  void testCalculateFailedWhenFailed() {
    DocumentHealthStatus status = DocumentHealthStatus.calculate(
        IngestionStatus.FAILED, null, null, "nomic-embed-text", 768);
    assertEquals(DocumentHealthStatus.FAILED, status);
  }
}
