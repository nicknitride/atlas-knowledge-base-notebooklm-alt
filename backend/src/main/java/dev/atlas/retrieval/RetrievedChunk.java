package dev.atlas.retrieval;

import java.util.UUID;

public record RetrievedChunk(
    UUID chunkId,
    UUID documentId,
    String documentFilename,
    int ordinal,
    String content,
    String sourceLocator,
    double similarity
) {}
