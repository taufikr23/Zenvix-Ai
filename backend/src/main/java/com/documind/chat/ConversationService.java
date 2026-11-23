package com.documind.chat;
import com.documind.common.ApiException;
import com.documind.document.DocumentRepository;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
@Service
public class ConversationService {
    private final ConversationRepository repository;private final DocumentRepository documents;
    private final RetrievalService retrieval;private final ChatGateway gateway;private final ChatProperties properties;
    private final ChatConcurrency concurrency;private final TransactionTemplate transaction;
    private final ChatAttachments attachments;
    public ConversationService(ConversationRepository repository,DocumentRepository documents,RetrievalService retrieval,
            ChatGateway gateway,ChatProperties properties,ChatConcurrency concurrency,org.springframework.transaction.PlatformTransactionManager manager,ChatAttachments attachments){
        this.repository=repository;this.documents=documents;this.retrieval=retrieval;this.gateway=gateway;
        this.properties=properties;this.concurrency=concurrency;transaction=new TransactionTemplate(manager);this.attachments=attachments;
    }
    public ChatDtos.Conversation create(UUID owner,UUID document){
        return transaction.execute(status->{
            if(!documents.lockActiveOwner(owner))throw new ApiException(HttpStatus.UNAUTHORIZED,"UNAUTHORIZED","Akun tidak aktif.");
            if(document!=null)retrieval.lockReady(owner,document);
            if(repository.count(owner)>=properties.maxConversations())
                throw new ApiException(HttpStatus.CONFLICT,"CONVERSATION_LIMIT","Batas percakapan tercapai. Hapus percakapan lama.");
            return repository.create(owner,document);
        });
    }
    public ChatDtos.Conversation get(UUID owner,UUID id){return repository.find(owner,id,false).orElseThrow(ChatErrors::missing);}
    public ChatDtos.ConversationPage list(UUID owner,int page,int size){
        ChatErrors.page(page,size);return new ChatDtos.ConversationPage(repository.list(owner,page,size),page,size,repository.count(owner));
    }
    public ChatDtos.TurnPage messages(UUID owner,UUID id,int page,int size){
        ChatErrors.page(page,size);get(owner,id);
        return new ChatDtos.TurnPage(repository.turns(owner,id,page,size).stream().map(turn->attachments.decorate(owner,turn)).toList(),page,size,repository.countTurns(owner,id));
    }
    public void delete(UUID owner,UUID id){if(!repository.delete(owner,id))throw ChatErrors.missing();}
    public ChatDtos.AskResult ask(UUID owner,UUID id,ChatDtos.Ask request){
        return ask(owner,id,request,List.of());
    }
    public ChatDtos.AskResult ask(UUID owner,UUID id,ChatDtos.Ask request,List<org.springframework.web.multipart.MultipartFile> files){
        return concurrency.execute(()->transaction.execute(status->{
            // Serialize attachment quota checks across conversations for the same owner.
            if(!files.isEmpty()&&!documents.lockActiveOwner(owner))
                throw new ApiException(HttpStatus.UNAUTHORIZED,"UNAUTHORIZED","Akun tidak aktif.");
            var initial=get(owner,id);
            // Urutan lock dokumen -> percakapan selalu sama dengan alur penghapusan dokumen.
            if(initial.documentId()!=null)retrieval.lockReady(owner,initial.documentId());
            var conversation=repository.find(owner,id,true).orElseThrow(ChatErrors::missing);
            if(conversation.documentId()!=null&&!files.isEmpty())
                throw new ApiException(HttpStatus.BAD_REQUEST,"LEGACY_CONVERSATION","Buat chat baru untuk mengirim lampiran.");
            String question=request.question().strip();
            var prepared=attachments.prepare(files);
            var existing=repository.existing(owner,id,request.requestId());
            if(existing.isPresent()){
                if(!existing.get().question().equals(question)||!attachments.matches(owner,existing.get().id(),prepared))
                    throw new ApiException(HttpStatus.CONFLICT,"REQUEST_ID_REUSED","requestId sudah dipakai untuk pertanyaan berbeda.");
                return new ChatDtos.AskResult(attachments.decorate(owner,existing.get()),false);
            }
            if(!properties.enabled())throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"CHAT_DISABLED","Fitur AI belum diaktifkan.");
            attachments.checkCapacity(owner,prepared);
            long count=repository.countTurns(owner,id);
            if(count>=properties.maxTurns())throw new ApiException(HttpStatus.CONFLICT,"TURN_LIMIT","Batas pesan percakapan tercapai. Buat percakapan baru.");
            ChatDtos.Answer answer;
            if(conversation.documentId()==null) {
                var history=repository.recentTurns(owner,id);
                var historyFiles=new HashMap<UUID,List<ChatAttachments.Content>>();
                // Keep the complete newest turns within a bounded multimodal context.
                long mediaBytes=prepared.stream().mapToLong(ChatAttachments.Content::mediaBytes).sum();
                int textCharacters=question.length()+prepared.stream().mapToInt(f->f.text().length()).sum();
                var retained=new ArrayList<ChatDtos.Turn>();
                for(int i=history.size()-1;i>=0;i--){
                    var turn=history.get(i);var previous=attachments.load(owner,turn.id());
                    long bytes=previous.stream().mapToLong(ChatAttachments.Content::mediaBytes).sum();
                    int chars=previous.stream().mapToInt(f->f.text().length()).sum()+turn.question().length()+turn.answer().length();
                    if(mediaBytes+bytes>ChatLimits.CONTEXT_MEDIA_BYTES||textCharacters+chars>ChatLimits.CONTEXT_CHARACTERS)break;
                    mediaBytes+=bytes;textCharacters+=chars;retained.add(turn);
                    if(!previous.isEmpty())historyFiles.put(turn.id(),previous);
                }
                Collections.reverse(retained);
                answer=prepared.isEmpty()&&historyFiles.isEmpty()?gateway.generalAnswer(owner,question,retained)
                        :gateway.generalAnswer(owner,question,retained,prepared,historyFiles);
            } else {
                var history=repository.recentQuestions(owner,id);
                String query=history.isEmpty()?question:String.join("\n",history)+"\nPertanyaan saat ini: "+question;
                var hits=retrieval.retrieve(owner,conversation.documentId(),query);
                answer=gateway.answer(owner,question,history,hits);
            }
            var turn=repository.save(owner,id,request.requestId(),(int)count,question,answer);
            attachments.save(owner,turn.id(),prepared);
            return new ChatDtos.AskResult(attachments.decorate(owner,turn),true);
        }));
    }
}
