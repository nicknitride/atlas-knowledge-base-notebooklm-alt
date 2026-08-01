package dev.atlas.workspaces;

import dev.atlas.support.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceLookup {
  private final WorkspaceRepository repository;

  WorkspaceLookup(WorkspaceRepository repository) {
    this.repository = repository;
  }

  public void requireExists(UUID id) {
    if (!repository.existsById(id)) {
      throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Workspace not found");
    }
  }

  public EmbeddingIdentity embeddingIdentity(UUID workspaceId) {
    Workspace workspace = repository.findById(workspaceId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Workspace not found"));
    if (!workspace.hasEmbeddingIdentity()) {
      return null;
    }
    return new EmbeddingIdentity(workspace.embeddingModel(), workspace.embeddingDimensions());
  }

  public void requireCompatibleEmbeddingConfig(UUID workspaceId, String model, int dimensions) {
    EmbeddingIdentity identity = embeddingIdentity(workspaceId);
    if (identity == null) {
      return;
    }
    if (!identity.model().equals(model) || identity.dimensions() != dimensions) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "EMBEDDING_CONFIG_MISMATCH",
          "Workspace vectors use embedding model '"
              + identity.model()
              + "' ("
              + identity.dimensions()
              + " dims). Re-index documents or restore that model before using '"
              + model
              + "'.");
    }
  }

  @Transactional
  public void stampEmbeddingIdentity(UUID workspaceId, String model, int dimensions) {
    Workspace workspace = repository.findById(workspaceId)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Workspace not found"));
    requireCompatibleEmbeddingConfig(workspaceId, model, dimensions);
    workspace.setEmbeddingIdentity(model, dimensions);
  }

  @Transactional
  public void clearEmbeddingIdentity(UUID workspaceId) {
    repository.findById(workspaceId).ifPresent(Workspace::clearEmbeddingIdentity);
  }

  public record EmbeddingIdentity(String model, int dimensions) {}
}
