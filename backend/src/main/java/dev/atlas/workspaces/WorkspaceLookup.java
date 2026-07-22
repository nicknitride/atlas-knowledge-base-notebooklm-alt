package dev.atlas.workspaces;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WorkspaceLookup {
  private final WorkspaceRepository repository;
  WorkspaceLookup(WorkspaceRepository repository) { this.repository = repository; }
  public void requireExists(UUID id) {
    if (!repository.existsById(id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found");
  }
}
