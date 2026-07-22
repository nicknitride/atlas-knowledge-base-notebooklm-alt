package dev.atlas.documents;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

@Component
class DocumentExtractor {
  List<ExtractedSection> extract(Path path, String contentType, String filename) throws IOException {
    if (isPdf(contentType, filename)) return extractPdf(path);
    if (isText(contentType, filename)) return List.of(new ExtractedSection(FilesText.read(path), "paragraph"));
    throw new IllegalArgumentException("Only PDF, Markdown, and plain-text documents are supported in P0");
  }
  boolean supports(String contentType, String filename) { return isPdf(contentType, filename) || isText(contentType, filename); }
  private List<ExtractedSection> extractPdf(Path path) throws IOException {
    List<ExtractedSection> sections = new ArrayList<>();
    try (PDDocument document = Loader.loadPDF(path.toFile())) {
      PDFTextStripper stripper = new PDFTextStripper();
      for (int page = 1; page <= document.getNumberOfPages(); page++) {
        stripper.setStartPage(page); stripper.setEndPage(page);
        String text = stripper.getText(document).trim();
        if (!text.isBlank()) sections.add(new ExtractedSection(text, "page:" + page));
      }
    }
    return sections;
  }
  private boolean isPdf(String type, String filename) { return "application/pdf".equals(type) || filename.toLowerCase().endsWith(".pdf"); }
  private boolean isText(String type, String filename) {
    String name = filename.toLowerCase();
    return type.startsWith("text/") || name.endsWith(".md") || name.endsWith(".markdown") || name.endsWith(".txt");
  }
  record ExtractedSection(String content, String locator) {}
  private static class FilesText { static String read(Path path) throws IOException { return java.nio.file.Files.readString(path, StandardCharsets.UTF_8); } }
}
