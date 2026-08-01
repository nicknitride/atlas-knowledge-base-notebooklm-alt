package dev.atlas.workspaces;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.atlas.support.ApiException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

    ApiException ex = assertThrows(ApiException.class, () -> workspaceLookup.requireExists(randomId));
    assertEquals("NOT_FOUND", ex.code());
  }

  @Test
  void requireExistsSucceedsForExistingWorkspace() {
    UUID workspaceId = UUID.randomUUID();
    when(repository.existsById(workspaceId)).thenReturn(true);

    assertDoesNotThrow(() -> workspaceLookup.requireExists(workspaceId));
  }
}
