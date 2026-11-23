package com.documind;

import com.documind.chat.*;
import com.documind.common.ApiException;
import com.documind.indexing.IndexingProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GeneralChatTest {
    private final ChatModel model=mock(ChatModel.class);
    private final ChatBudget budget=mock(ChatBudget.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<ChatModel> models=mock(ObjectProvider.class);
    private final ObjectMapper mapper=new ObjectMapper();
    private final ChatGateway gateway=new ChatGateway(models,mapper,budget,new GroundedAnswerValidator(mapper));
    private final UUID owner=UUID.randomUUID();

    private void respond(String json){
        when(models.getIfAvailable()).thenReturn(model);
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(json)))));
    }
    @Test void generalChatUsesBothRolesAndReturnsAnswerWithoutDocumentSources(){
        respond("{\"answer\":\"Here is the revised code.\"}");
        var turn=new ChatDtos.Turn(UUID.randomUUID(),UUID.randomUUID(),0,"Buat fungsi", "def hello(): pass",false,List.of(),Instant.now());
        var answer=gateway.generalAnswer(owner,"Explain that code in English",List.of(turn));
        assertThat(answer.text()).isEqualTo("Here is the revised code.");
        assertThat(answer.found()).isFalse();
        assertThat(answer.sources()).isEmpty();
        var capture=org.mockito.ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(capture.capture());
        var messages=capture.getValue().getInstructions();
        assertThat(messages).hasSize(4);
        assertThat(messages.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(messages.get(1)).isInstanceOf(UserMessage.class);
        assertThat(messages.get(2)).isInstanceOf(AssistantMessage.class);
        assertThat(messages.get(2).getText()).isEqualTo(turn.answer());
        assertThat(messages.get(3).getText()).isEqualTo("Explain that code in English");
        verify(budget).reserve(owner,messages.stream().mapToInt(m->m.getText().length()).sum());
    }
    @Test void malformedAndEmptyResponsesAreRejected(){
        for(String value:List.of("not JSON","{}","{\"answer\":\" \"}","{\"answer\":4}")){
            respond(value);
            assertThatThrownBy(()->gateway.generalAnswer(owner,"Hello",List.of())).isInstanceOf(ApiException.class);
        }
    }
    @Test void attachmentsReachTheModelAndRemainAvailableOnFollowup(){
        respond("{\"answer\":\"Gambar dan catatan terbaca.\"}");
        var image=new ChatAttachments.Content(UUID.randomUUID(),"diagram.png","image/png",new byte[]{1,2,3},"","hash");
        var text=new ChatAttachments.Content(UUID.randomUUID(),"notes.txt","text/plain","budget 100".getBytes(),"budget 100","hash");
        var turn=new ChatDtos.Turn(UUID.randomUUID(),UUID.randomUUID(),0,"Lihat ini","Sudah",false,List.of(),Instant.now());
        gateway.generalAnswer(owner,"Bandingkan",List.of(turn),List.of(text),Map.of(turn.id(),List.of(image)));
        var capture=org.mockito.ArgumentCaptor.forClass(Prompt.class);verify(model).call(capture.capture());
        var messages=capture.getValue().getInstructions();
        assertThat(((UserMessage)messages.get(1)).getMedia()).hasSize(1);
        assertThat(((UserMessage)messages.get(1)).getMedia().get(0).getMimeType().toString()).isEqualTo("image/png");
        assertThat(messages.get(3).getText()).contains("notes.txt","budget 100","Bandingkan");
    }
    @Test void providerErrorDoesNotExposeDetails(){
        when(models.getIfAvailable()).thenReturn(model);
        when(model.call(any(Prompt.class))).thenThrow(new IllegalStateException("private provider details"));
        assertThatThrownBy(()->gateway.generalAnswer(owner,"Hello",List.of())).isInstanceOf(ApiException.class)
                .hasMessageNotContaining("private provider details");
    }
    @Test void missingModelDoesNotReserveBudget(){
        assertThatThrownBy(()->gateway.generalAnswer(owner,"Hello",List.of())).isInstanceOf(ApiException.class);
        verifyNoInteractions(budget);
    }
    @Test void exhaustedBudgetPreventsProviderCall(){
        when(models.getIfAvailable()).thenReturn(model);
        doThrow(new IllegalStateException("limit")).when(budget).reserve(eq(owner),anyInt());
        assertThatThrownBy(()->gateway.generalAnswer(owner,"Hello",List.of())).hasMessage("limit");
        verifyNoInteractions(model);
    }
    private ChatProperties properties(){return new ChatProperties(true,"gemini-3.5-flash-lite",2000,1024,5,.45,6000,50,100,500,50,2000000,200000);}
    @Test void chatClientAcceptsDisabledEmbeddingWithKey(){
        try(var client=new ChatConfig().chatClient(properties(),new IndexingProperties(false,"test-only-key",2000,1000,1000))){
            assertThat(client).isNotNull();
        }
    }
    @Test void chatClientRejectsMissingKeyEvenWhenEmbeddingDisabled(){
        assertThatThrownBy(()->new ChatConfig().chatClient(properties(),new IndexingProperties(false,"",2000,1000,1000)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("GEMINI_API_KEY");
    }
}
