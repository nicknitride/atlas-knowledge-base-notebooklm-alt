package dev.atlas.chat;

import java.util.UUID;

public record CitationResponse(
    UUID chunkId,
    UUID documentId,
    String documentFilename,
    int ordinal,
    String sourceLocator,
    String snippet,
    double similarity
) {}
