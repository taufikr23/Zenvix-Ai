package com.documind;

import com.documind.chat.*;
import com.documind.document.DocumentRepository;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GeneralConversationServiceTest {
    private final UUID owner=UUID.randomUUID(),id=UUID.randomUUID(),requestId=UUID.randomUUID();
    private final ConversationRepository repository=mock(ConversationRepository.class);
    private final DocumentRepository documents=mock(DocumentRepository.class);
    private final RetrievalService retrieval=mock(RetrievalService.class);
    private final ChatGateway gateway=mock(ChatGateway.class);
    private final ChatAttachments attachments=mock(ChatAttachments.class);
    private final ChatDtos.Conversation conversation=new ChatDtos.Conversation(id,null,null,"Percakapan baru",Instant.now());
    private final ChatDtos.Turn turn=new ChatDtos.Turn(UUID.randomUUID(),requestId,0,"Halo","Halo kembali",false,List.of(),Instant.now());

    private ConversationService service(boolean enabled){
        var properties=new ChatProperties(enabled,"gemini-3.5-flash-lite",2000,1024,5,.45,6000,50,100,500,50,2000000,200000);
        var manager=mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(attachments.decorate(any(),any())).thenAnswer(call->call.getArgument(1));
        when(attachments.matches(any(),any(),any())).thenReturn(true);
        return new ConversationService(repository,documents,retrieval,gateway,properties,new ChatConcurrency(),manager,attachments);
    }
    private void conversationExists(){
        when(repository.find(owner,id,false)).thenReturn(Optional.of(conversation));
        when(repository.find(owner,id,true)).thenReturn(Optional.of(conversation));
        when(repository.existing(owner,id,requestId)).thenReturn(Optional.empty());
    }
    @Test void createsGeneralConversationEvenBeforeProviderActivation(){
        when(documents.lockActiveOwner(owner)).thenReturn(true);
        when(repository.create(owner,null)).thenReturn(conversation);
        assertThat(service(false).create(owner,null)).isEqualTo(conversation);
        verifyNoInteractions(retrieval,gateway);
    }
    @Test void generalAskNeverRetrievesDocumentsAndSavesProviderAnswer(){
        conversationExists();
        var history=List.of(turn);
        var answer=new ChatDtos.Answer("Penjelasan",false,List.of());
        when(repository.recentTurns(owner,id)).thenReturn(history);
        when(gateway.generalAnswer(owner,"Jelaskan",history)).thenReturn(answer);
        when(repository.save(owner,id,requestId,0,"Jelaskan",answer)).thenReturn(turn);
        assertThat(service(true).ask(owner,id,new ChatDtos.Ask(requestId," Jelaskan ")).created()).isTrue();
        verify(gateway).generalAnswer(owner,"Jelaskan",history);
        verify(repository).save(owner,id,requestId,0,"Jelaskan",answer);
        verifyNoInteractions(retrieval);
    }
    @Test void retriesReplayWithoutProviderEvenWhenChatIsDisabled(){
        conversationExists();
        when(repository.existing(owner,id,requestId)).thenReturn(Optional.of(turn));
        var result=service(false).ask(owner,id,new ChatDtos.Ask(requestId,"Halo"));
        assertThat(result.created()).isFalse();
        assertThat(result.turn()).isEqualTo(turn);
        verifyNoInteractions(retrieval,gateway);
    }
    @Test void turnLimitPreventsModelCall(){
        conversationExists();
        when(repository.countTurns(owner,id)).thenReturn(50L);
        assertThatThrownBy(()->service(true).ask(owner,id,new ChatDtos.Ask(requestId,"Halo")))
                .isInstanceOf(com.documind.common.ApiException.class).hasMessageContaining("Batas pesan");
        verifyNoInteractions(retrieval,gateway);
    }
    @Test void followupRetainsLongMessagesAndLongAttachments(){
        conversationExists();
        var previous=new ChatDtos.Turn(UUID.randomUUID(),UUID.randomUUID(),0,"p".repeat(100000),"Jawaban sebelumnya",false,List.of(),Instant.now());
        var file=new ChatAttachments.Content(UUID.randomUUID(),"long.txt","text/plain",new byte[]{1},"d".repeat(400000)+"PENUTUP", "hash");
        when(repository.recentTurns(owner,id)).thenReturn(List.of(previous));
        when(attachments.load(owner,previous.id())).thenReturn(List.of(file));
        var answer=new ChatDtos.Answer("Penutup dokumen",false,List.of());
        when(gateway.generalAnswer(eq(owner),eq("Jelaskan bagian penutup"),eq(List.of(previous)),eq(List.of()),anyMap())).thenReturn(answer);
        when(repository.save(owner,id,requestId,0,"Jelaskan bagian penutup",answer)).thenReturn(turn);
        service(true).ask(owner,id,new ChatDtos.Ask(requestId,"Jelaskan bagian penutup"));
        verify(gateway).generalAnswer(owner,"Jelaskan bagian penutup",List.of(previous),List.of(),Map.of(previous.id(),List.of(file)));
    }
    @Test void requestValidationAcceptsTheNewBoundaryAndRejectsOversizedMessages(){
        try(var factory=jakarta.validation.Validation.buildDefaultValidatorFactory()){
            var validator=factory.getValidator();
            assertThat(validator.validate(new ChatDtos.Ask(requestId,"x".repeat(100000)))).isEmpty();
            assertThat(validator.validate(new ChatDtos.Ask(requestId,"x".repeat(100001)))).isNotEmpty();
        }
    }
}
