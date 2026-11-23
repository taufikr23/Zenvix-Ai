package com.documind;
import com.documind.chat.*;
import com.documind.common.ApiException;
import com.documind.document.FileValidator;
import com.documind.processing.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatAttachmentsTest {
    private final DocumentExtractor extractor=new DocumentExtractor(new ProcessingProperties(1000,150,2000000,3000,60));
    private final ChatAttachments attachments=new ChatAttachments(mock(JdbcTemplate.class),new FileValidator(new MultipartProperties()),extractor);
    @Test void readsTextAndRealImageBytes()throws Exception {
        var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",out);
        var files=attachments.prepare(List.of(new MockMultipartFile("files","notes.txt","text/plain","Rapat pukul 10".getBytes()),
                new MockMultipartFile("files","image.png","application/octet-stream",out.toByteArray())));
        assertThat(files.get(0).text()).isEqualTo("Rapat pukul 10");
        assertThat(files.get(1).mediaType()).isEqualTo("image/png");
        assertThat(files.get(1).bytes()).isEqualTo(out.toByteArray());
    }
    @Test void rejectsFakeImagesUnsafeNamesEmptyFilesAndTooManyFiles(){
        for(var file:List.of(new MockMultipartFile("files","fake.png","image/png","not an image".getBytes()),
                new MockMultipartFile("files","../notes.txt","text/plain","hello".getBytes()),
                new MockMultipartFile("files","empty.txt","text/plain",new byte[0]))){
            assertThatThrownBy(()->attachments.prepare(List.of(file))).isInstanceOf(ApiException.class);
        }
        var file=new MockMultipartFile("files","notes.txt","text/plain","hello".getBytes());
        assertThatThrownBy(()->attachments.prepare(Collections.nCopies(4,file))).hasMessageContaining("Maksimal 3");
        assertThatThrownBy(()->attachments.prepare(List.of(new MockMultipartFile("files","long.txt","text/plain","x".repeat(500001).getBytes()))))
                .hasMessageContaining("500.000");
    }
    @Test void longTextKeepsTheEndingAndTheLimitAppliesToTheCombinedFiles(){
        String text="awal\n"+"x".repeat(499988)+"\nAKHIR";
        var file=new MockMultipartFile("files","long.txt","text/plain",text.getBytes());
        assertThat(attachments.prepare(List.of(file)).getFirst().text()).isEqualTo(text);
        assertThatThrownBy(()->attachments.prepare(List.of(file,new MockMultipartFile("files","more.txt","text/plain","more".repeat(100).getBytes()))))
                .hasMessageContaining("500.000");
    }
    @Test void scannedDocxPreservesEmbeddedPicturesAndCanReachTheModel()throws Exception {
        var png=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(3,4,BufferedImage.TYPE_INT_RGB),"png",png);
        var jpg=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(4,3,BufferedImage.TYPE_INT_RGB),"jpeg",jpg);
        var out=new ByteArrayOutputStream();
        try(var document=new org.apache.poi.xwpf.usermodel.XWPFDocument()){
            document.createParagraph().createRun().addPicture(new java.io.ByteArrayInputStream(png.toByteArray()),org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_PNG,"page1.png",10000,10000);
            document.createParagraph().createRun().addPicture(new java.io.ByteArrayInputStream(jpg.toByteArray()),org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_JPEG,"page2.jpg",10000,10000);
            document.write(out);
        }
        var content=attachments.prepare(List.of(new MockMultipartFile("files","scan.docx",AttachmentVisuals.DOCX,out.toByteArray()))).getFirst();
        assertThat(content.text()).isBlank();
        assertThat(content.visuals()).extracting(AttachmentVisuals.Visual::mediaType).containsExactly("image/png","image/jpeg");
        assertThat(content.visuals().getFirst().bytes()).isEqualTo(png.toByteArray());
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        var model=mock(org.springframework.ai.chat.model.ChatModel.class);
        @SuppressWarnings("unchecked") var models=(org.springframework.beans.factory.ObjectProvider<org.springframework.ai.chat.model.ChatModel>)mock(org.springframework.beans.factory.ObjectProvider.class);
        when(models.getIfAvailable()).thenReturn(model);
        when(model.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(new org.springframework.ai.chat.model.ChatResponse(List.of(
                new org.springframework.ai.chat.model.Generation(new org.springframework.ai.chat.messages.AssistantMessage("{\"answer\":\"Terbaca\"}")))));
        var gateway=new ChatGateway(models,mapper,mock(ChatBudget.class),new GroundedAnswerValidator(mapper));
        gateway.generalAnswer(UUID.randomUUID(),"ini isinya apa?",List.of(),List.of(content),Map.of());
        var capture=org.mockito.ArgumentCaptor.forClass(org.springframework.ai.chat.prompt.Prompt.class);verify(model).call(capture.capture());
        assertThat(capture.getValue().getUserMessage().getMedia()).hasSize(2);
    }
    @Test void scannedPdfIsForwardedAsPdfInsteadOfRejectedForMissingText()throws Exception {
        var out=new ByteArrayOutputStream();
        try(var pdf=new org.apache.pdfbox.pdmodel.PDDocument()){
            pdf.addPage(new org.apache.pdfbox.pdmodel.PDPage());pdf.save(out);
        }
        var file=attachments.prepare(List.of(new MockMultipartFile("files","scan.pdf","application/pdf",out.toByteArray()))).getFirst();
        assertThat(file.text()).isBlank();
        assertThat(file.visuals()).hasSize(1);
        assertThat(file.visuals().getFirst().mediaType()).isEqualTo("application/pdf");
        assertThat(file.visuals().getFirst().bytes()).isEqualTo(out.toByteArray());
    }
    @Test
    @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="documind.test.docx",matches=".+")
    void readsUserSuppliedScannedDocxWithoutCopyingPrivateDataIntoFixtures()throws Exception {
        var bytes=java.nio.file.Files.readAllBytes(java.nio.file.Path.of(System.getProperty("documind.test.docx")));
        var file=attachments.prepare(List.of(new MockMultipartFile("files","regression.docx",AttachmentVisuals.DOCX,bytes))).getFirst();
        assertThat(file.text()).isBlank();assertThat(file.visuals()).hasSize(4);
        assertThat(file.visuals()).allSatisfy(image->assertThat(image.bytes()).isNotEmpty());
    }
}
