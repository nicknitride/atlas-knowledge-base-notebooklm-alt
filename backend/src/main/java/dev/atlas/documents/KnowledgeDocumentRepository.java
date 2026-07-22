package dev.atlas.documents;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, UUID> {
  List<KnowledgeDocument> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);
  Optional<KnowledgeDocument> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
