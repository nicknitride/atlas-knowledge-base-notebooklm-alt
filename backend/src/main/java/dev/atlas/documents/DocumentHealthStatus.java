package dev.atlas.documents;

public enum DocumentHealthStatus {
  READY,
  STALE,
  PENDING,
  FAILED;

  public static DocumentHealthStatus calculate(
      IngestionStatus ingestionStatus,
      String docEmbeddingModel,
      Integer docEmbeddingDimensions,
      String activeModel,
      int activeDimensions) {
    if (ingestionStatus == IngestionStatus.FAILED) {
      return FAILED;
    }
    if (ingestionStatus == IngestionStatus.PENDING || ingestionStatus == IngestionStatus.PROCESSING) {
      return PENDING;
    }
    if (ingestionStatus == IngestionStatus.COMPLETE) {
      if (activeModel.equals(docEmbeddingModel)
          && docEmbeddingDimensions != null
          && docEmbeddingDimensions.intValue() == activeDimensions) {
        return READY;
      }
      return STALE;
    }
    return FAILED;
  }
}
