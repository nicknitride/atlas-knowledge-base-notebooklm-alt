package dev.atlas.workspaces;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class WorkspaceEmbeddingIdentityTest {
  @Test
  void storesAndClearsEmbeddingIdentity() {
    Workspace workspace = new Workspace("Notes");
    assertFalse(workspace.hasEmbeddingIdentity());

    workspace.setEmbeddingIdentity("nomic-embed-text", 768);
    assertTrue(workspace.hasEmbeddingIdentity());
    assertEquals("nomic-embed-text", workspace.embeddingModel());
    assertEquals(768, workspace.embeddingDimensions());

    workspace.clearEmbeddingIdentity();
    assertFalse(workspace.hasEmbeddingIdentity());
  }
}
