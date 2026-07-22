package dev.atlas.documents;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class FileStorage {
  private final Path root;
  FileStorage(@Value("${atlas.storage-dir:./data/uploads}") String storageDir) { root = Path.of(storageDir).toAbsolutePath().normalize(); }
  String store(UUID workspaceId, String filename, InputStream stream) throws IOException {
    String safeName = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    Path target = root.resolve(workspaceId.toString()).resolve(UUID.randomUUID() + "-" + safeName).normalize();
    if (!target.startsWith(root)) throw new IOException("Invalid storage path");
    Files.createDirectories(target.getParent());
    Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING);
    return root.relativize(target).toString();
  }
  Path resolve(String key) throws IOException {
    Path target = root.resolve(key).normalize();
    if (!target.startsWith(root)) throw new IOException("Invalid storage key");
    return target;
  }
}
