package dev.atlas.chat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {
  List<Conversation> findByWorkspaceIdOrderByUpdatedAtDesc(UUID workspaceId);
  Optional<Conversation> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
