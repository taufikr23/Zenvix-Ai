package com.documind;

import com.documind.processing.*;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import static org.assertj.core.api.Assertions.*;

class ExtractionTest {
    private final ProcessingProperties properties = new ProcessingProperties(100, 20, 10000, 100, 60);
    private final DocumentExtractor extractor = new DocumentExtractor(properties);

    @Test
    void pdfKeepsActualPageNumbersIncludingBlankPages() throws Exception {
        try (var pdf = new PDDocument(); var out = new ByteArrayOutputStream()) {
            for (String text : List.of("Halaman satu", "", "Halaman tiga")) {
                var page = new PDPage();
                pdf.addPage(page);
                if (!text.isEmpty()) {
                    try (var content = new PDPageContentStream(pdf, page)) {
                        content.beginText();
                        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                        content.newLineAtOffset(50, 700);
                        content.showText(text);
                        content.endText();
                    }
                }
            }
            pdf.save(out);
            var extracted = extractor.extract("PDF", out.toByteArray());
            assertThat(extracted.sourcePages()).isEqualTo(3);
            var chunks = new TextChunker(properties).split(extracted);
            assertThat(chunks).extracting(TextChunker.Chunk::pageNumber).containsExactly(1, 3);
            assertThat(chunks.get(1).content()).contains("Halaman tiga").doesNotContain("Halaman satu");
        }
    }

    @Test
    void docxPreservesParagraphAndTableOrderWithoutInventingPages() throws Exception {
        try (var document = new XWPFDocument(); var out = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("Sebelum tabel");
            var table = document.createTable(1, 2);
            table.getRow(0).getCell(0).setText("Sel pertama");
            table.getRow(0).getCell(1).setText("Sel kedua");
            document.createParagraph().createRun().setText("Sesudah tabel");
            document.write(out);
            var extracted = extractor.extract("DOCX", out.toByteArray());
            String text = extracted.sections().getFirst().text();
            assertThat(text.indexOf("Sebelum tabel")).isLessThan(text.indexOf("Sel pertama"));
            assertThat(text.indexOf("Sel pertama")).isLessThan(text.indexOf("Sel kedua"));
            assertThat(text.indexOf("Sel kedua")).isLessThan(text.indexOf("Sesudah tabel"));
            assertThat(extracted.sourcePages()).isNull();
            assertThat(extracted.sections().getFirst().pageNumber()).isNull();
        }
    }

    @Test
    void unicodeChunksOverlapWithoutLosingTextOrSplittingEmoji() {
        String source = "abc😀 ".repeat(70).strip();
        var chunks = new TextChunker(properties).split(
                new ExtractedText(List.of(new ExtractedText.Section(1, null, source)), null));
        StringBuilder rebuilt = new StringBuilder(chunks.getFirst().content());
        for (int index = 1; index < chunks.size(); index++) {
            String previous = chunks.get(index - 1).content(), next = chunks.get(index).content();
            int suffix = previous.offsetByCodePoints(previous.length(), -20);
            int prefix = next.offsetByCodePoints(0, 20);
            assertThat(previous.substring(suffix)).isEqualTo(next.substring(0, prefix));
            rebuilt.append(next.substring(prefix));
        }
        assertThat(rebuilt.toString()).isEqualTo(source);
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.characterCount()).isLessThanOrEqualTo(100);
            assertThat(chunk.content().codePoints().anyMatch(c -> c >= 0xD800 && c <= 0xDFFF)).isFalse();
        });
    }

    @Test
    void excessiveTextAndChunkCountFailInsteadOfSilentlyTruncating() {
        var limited = new ProcessingProperties(100, 20, 100, 1, 60);
        assertThatThrownBy(() -> new DocumentExtractor(limited).extract("TXT",
                "x".repeat(101).getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ExtractionFailure.class).hasMessage("TEXT_TOO_LARGE");
        assertThatThrownBy(() -> new TextChunker(limited).split(
                new ExtractedText(List.of(new ExtractedText.Section(1, null, "x".repeat(101))), null)))
                .isInstanceOf(ExtractionFailure.class).hasMessage("TOO_MANY_CHUNKS");
    }

    @Test
    void emptyTextIsRejectedAndLineEndingsAreNormalized() {
        assertThatThrownBy(() -> new TextChunker(properties).split(
                new ExtractedText(List.of(new ExtractedText.Section(1, 1, " \n")), 1)))
                .isInstanceOf(ExtractionFailure.class).hasMessage("NO_EXTRACTABLE_TEXT");
        var text = extractor.extract("TXT", "\uFEFFPertama\r\nKedua\rKetiga".getBytes(StandardCharsets.UTF_8));
        assertThat(text.sections().getFirst().text()).isEqualTo("Pertama\nKedua\nKetiga");
    }
}
