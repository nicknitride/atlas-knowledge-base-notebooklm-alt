package dev.atlas.workspaces;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class WorkspaceIsolationTest {
  private WorkspaceRepository repository;
  private WorkspaceLookup workspaceLookup;

  @BeforeEach
  void setUp() {
    repository = mock(WorkspaceRepository.class);
    workspaceLookup = new WorkspaceLookup(repository);
  }

  @Test
  void requireExistsThrowsNotFoundForMissingWorkspace() {
    UUID randomId = UUID.randomUUID();
    when(repository.existsById(randomId)).thenReturn(false);

    assertThrows(ResponseStatusException.class, () -> workspaceLookup.requireExists(randomId));
  }

  @Test
  void requireExistsSucceedsForExistingWorkspace() {
    UUID workspaceId = UUID.randomUUID();
    when(repository.existsById(workspaceId)).thenReturn(true);

    assertDoesNotThrow(() -> workspaceLookup.requireExists(workspaceId));
  }
}
