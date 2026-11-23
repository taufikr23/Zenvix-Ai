package com.documind;
import com.documind.chat.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class GroundedAnswerTest {
    private final GroundedAnswerValidator validator=new GroundedAnswerValidator(new ObjectMapper());
    private final List<RetrievalRepository.Hit> hits=List.of(new RetrievalRepository.Hit(
            UUID.randomUUID(),UUID.randomUUID(),0,2,"sumber-asli.pdf","Biaya pendaftaran adalah Rp150.000.\nAcara berlangsung tanggal 20 November.",0.8));
    @Test void verifiedQuoteUsesOnlyServerMetadata() {
        var answer=validator.validate("""
                {"found":true,"passages":[{"sourceId":"S1","quote":"Biaya pendaftaran adalah Rp150.000.","filename":"palsu.pdf","pageNumber":999}]}
                """,hits);
        assertThat(answer.found()).isTrue();
        assertThat(answer.sources().getFirst().filename()).isEqualTo("sumber-asli.pdf");
        assertThat(answer.sources().getFirst().pageNumber()).isEqualTo(2);
        assertThat(answer.text()).contains("[1]").doesNotContain("palsu.pdf");
    }
    @Test void whitespaceNormalizationAcceptsOriginalTextAcrossLineBreaks() {
        var answer=validator.validate("""
                {"found":true,"passages":[{"sourceId":"S1","quote":"Biaya pendaftaran adalah Rp150.000. Acara berlangsung tanggal 20 November."}]}
                """,hits);
        assertThat(answer.found()).isTrue();
    }
    @Test void hallucinatedSourcesQuotesAndMalformedRepliesAreRejected() {
        for(String response:List.of(
                "{\"found\":true,\"passages\":[{\"sourceId\":\"S2\",\"quote\":\"Biaya pendaftaran adalah Rp150.000.\"}]}",
                "{\"found\":true,\"passages\":[{\"sourceId\":\"S1\",\"quote\":\"Biaya pendaftaran adalah Rp900.000.\"}]}",
                "{\"found\":true,\"passages\":[]}","{\"found\":false,\"passages\":[{}]}","null","jawaban tanpa JSON")) {
            assertThatThrownBy(()->validator.validate(response,hits)).isInstanceOf(com.documind.common.ApiException.class);
        }
    }
    @Test void absentInformationReturnsNoFabricatedReference() {
        var answer=validator.validate("{\"found\":false,\"passages\":[]}",hits);
        assertThat(answer.found()).isFalse();assertThat(answer.sources()).isEmpty();
        assertThat(answer.text()).isEqualTo(GroundedAnswerValidator.NOT_FOUND);
    }
    @Test void duplicateReferencesAreRejected() {
        assertThatThrownBy(()->validator.validate("""
                {"found":true,"passages":[
                {"sourceId":"S1","quote":"Biaya pendaftaran adalah Rp150.000."},
                {"sourceId":"S1","quote":"Acara berlangsung tanggal 20 November."}]}
                """,hits)).isInstanceOf(com.documind.common.ApiException.class);
    }
    @Test void acceptsContentOrTextKeyAsQuoteFallback() {
        var answerContent=validator.validate("""
                {"found":true,"passages":[{"sourceId":"S1","content":"Biaya pendaftaran adalah Rp150.000."}]}
                """,hits);
        assertThat(answerContent.found()).isTrue();
        assertThat(answerContent.sources().getFirst().quote()).isEqualTo("Biaya pendaftaran adalah Rp150.000.");

        var answerText=validator.validate("""
                {"found":true,"passages":[{"source_id":"S1","text":"Biaya pendaftaran adalah Rp150.000."}]}
                """,hits);
        assertThat(answerText.found()).isTrue();
        assertThat(answerText.sources().getFirst().quote()).isEqualTo("Biaya pendaftaran adalah Rp150.000.");
    }
    @Test void generalQuestionWithoutPassagesReturnsAnswerText() {
        var answer=validator.validate("""
                {"found":false,"answer":"1 + 1 = 2.","passages":[]}
                """,hits);
        assertThat(answer.found()).isFalse();
        assertThat(answer.sources()).isEmpty();
        assertThat(answer.text()).isEqualTo("1 + 1 = 2.");
    }
    @Test void documentAnswerWithExplanationFieldUsesAnswerText() {
        var answer=validator.validate("""
                {"found":true,"answer":"Biaya pendaftaran adalah Rp150.000 [1].","passages":[{"sourceId":"S1","quote":"Biaya pendaftaran adalah Rp150.000."}]}
                """,hits);
        assertThat(answer.found()).isTrue();
        assertThat(answer.sources()).hasSize(1);
        assertThat(answer.text()).contains("Biaya pendaftaran adalah Rp150.000 [1].");
    }
}
