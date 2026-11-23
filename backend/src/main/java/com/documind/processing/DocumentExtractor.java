package com.documind.processing;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Component;

@Component
public class DocumentExtractor {
    private final ProcessingProperties properties;
    public DocumentExtractor(ProcessingProperties properties) { this.properties = properties; }

    public ExtractedText extract(String type, byte[] bytes) {
        var budget = new Budget(properties.maxCharacters(), properties.timeoutSeconds());
        try {
            return switch (type) {
                case "PDF" -> pdf(bytes, budget);
                case "DOCX" -> docx(bytes, budget);
                case "TXT" -> {
                    var writer = new LimitedWriter(budget);
                    writer.write(new String(bytes, StandardCharsets.UTF_8));
                    yield new ExtractedText(List.of(new ExtractedText.Section(1, null, normalize(writer.toString()))), null);
                }
                default -> throw new ExtractionFailure("UNSUPPORTED_FILE");
            };
        } catch (ExtractionFailure failure) { throw failure; }
        catch (IOException | RuntimeException failure) { throw new ExtractionFailure("EXTRACTION_FAILED"); }
    }

    private ExtractedText pdf(byte[] bytes, Budget budget) throws IOException {
        var sections = new ArrayList<ExtractedText.Section>();
        try (var document = Loader.loadPDF(bytes)) {
            if (document.isEncrypted()) { throw new ExtractionFailure("ENCRYPTED_PDF"); }
            var stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            int pages = document.getNumberOfPages();
            for (int page = 1; page <= pages; page++) {
                budget.check();
                var writer = new LimitedWriter(budget);
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                stripper.writeText(document, writer);
                sections.add(new ExtractedText.Section(page, page, normalize(writer.toString())));
            }
            return new ExtractedText(List.copyOf(sections), pages);
        }
    }

    private ExtractedText docx(byte[] bytes, Budget budget) throws IOException {
        var writer = new LimitedWriter(budget);
        try (var document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            appendBody(document.getBodyElements(), writer, 0);
        }
        return new ExtractedText(List.of(new ExtractedText.Section(1, null, normalize(writer.toString()))), null);
    }

    private void appendBody(List<IBodyElement> elements, LimitedWriter writer, int depth) throws IOException {
        if (depth > 20) { throw new ExtractionFailure("DOCUMENT_TOO_COMPLEX"); }
        for (var element : elements) {
            writer.budget.check();
            if (element instanceof XWPFParagraph paragraph) {
                writer.write(paragraph.getText());
                writer.write("\n");
            } else if (element instanceof XWPFTable table) {
                for (var row : table.getRows()) {
                    for (var cell : row.getTableCells()) {
                        appendBody(cell.getBodyElements(), writer, depth + 1);
                        writer.write("\t");
                    }
                    writer.write("\n");
                }
            } else if (element instanceof XWPFSDT control) {
                writer.write(control.getContent().getText());
                writer.write("\n");
            }
        }
    }

    private String normalize(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n').replace("\uFEFF", "")
                .replaceAll("[\\p{Cc}&&[^\\n\\t]]", "").strip();
    }

    private static class Budget {
        private int remaining;
        private final long deadline;
        Budget(int maximum, int seconds) {
            remaining = maximum;
            deadline = System.nanoTime() + seconds * 1_000_000_000L;
        }
        void check() {
            if (System.nanoTime() > deadline || Thread.currentThread().isInterrupted()) {
                throw new ExtractionFailure("PROCESSING_TIMEOUT");
            }
        }
        void take(int size) {
            check();
            if (size > remaining) { throw new ExtractionFailure("TEXT_TOO_LARGE"); }
            remaining -= size;
        }
    }

    private static class LimitedWriter extends Writer {
        private final Budget budget;
        private final StringBuilder text = new StringBuilder();
        LimitedWriter(Budget budget) { this.budget = budget; }
        @Override public void write(char[] chars, int offset, int length) {
            budget.take(length);
            text.append(chars, offset, length);
        }
        @Override public void flush() {}
        @Override public void close() {}
        @Override public String toString() { return text.toString(); }
    }
}
