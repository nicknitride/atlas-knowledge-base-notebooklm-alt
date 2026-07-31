package dev.atlas.workspaces;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import dev.atlas.support.ApiException;

@RestController
@RequestMapping("/api/workspaces")
class WorkspaceController {
  private final WorkspaceRepository repository;

  WorkspaceController(WorkspaceRepository repository) { this.repository = repository; }

  @GetMapping
  List<WorkspaceResponse> list() {
    return repository.findAll().stream().map(WorkspaceResponse::from).toList();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  WorkspaceResponse create(@Valid @RequestBody WorkspaceRequest request) {
    return WorkspaceResponse.from(repository.save(new Workspace(request.name().trim())));
  }

  @PutMapping("/{id}")
  @Transactional
  WorkspaceResponse rename(@PathVariable UUID id, @Valid @RequestBody WorkspaceRequest request) {
    Workspace workspace = find(id);
    workspace.rename(request.name().trim());
    return WorkspaceResponse.from(workspace);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(@PathVariable UUID id) { repository.delete(find(id)); }

  private Workspace find(UUID id) {
    return repository.findById(id)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Workspace not found"));
  }

  record WorkspaceRequest(@NotBlank @Size(max = 120) String name) {}
  record WorkspaceResponse(UUID id, String name, Instant createdAt) {
    static WorkspaceResponse from(Workspace workspace) {
      return new WorkspaceResponse(workspace.id(), workspace.name(), workspace.createdAt());
    }
  }
}
