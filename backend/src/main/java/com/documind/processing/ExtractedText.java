package com.documind.processing;
import java.util.List;
public record ExtractedText(List<Section> sections, Integer sourcePages) {
    public record Section(int number, Integer pageNumber, String text) {
        @Override public String toString() { return "Section[number=" + number + "]"; }
    }
    @Override public String toString() { return "ExtractedText[sections=" + sections.size() + "]"; }
}
