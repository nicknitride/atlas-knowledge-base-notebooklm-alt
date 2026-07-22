package dev.atlas.documents;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocumentExtractorTest {
  @TempDir Path tempDir;

  @Test
  void extractsPlainTextAsACitationReadySection() throws Exception {
    Path file = tempDir.resolve("notes.md");
    Files.writeString(file, "# Atlas\n\nDocuments need provenance.");

    var sections = new DocumentExtractor().extract(file, "text/markdown", "notes.md");

    assertThat(sections).singleElement().satisfies(section -> {
      assertThat(section.content()).contains("Documents need provenance.");
      assertThat(section.locator()).isEqualTo("paragraph");
    });
  }
}
