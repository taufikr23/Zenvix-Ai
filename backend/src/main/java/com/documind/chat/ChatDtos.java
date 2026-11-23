package com.documind.chat;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ChatDtos {
    private ChatDtos() {}
    public record CreateConversation(UUID documentId) {}
    public record Ask(@NotNull UUID requestId, @NotBlank @Size(max=ChatLimits.MESSAGE_CHARACTERS) String question) {
        @Override public String toString() { return "Ask[requestId="+requestId+"]"; }
    }
    public record Search(@NotBlank @Size(max=1000) String query) {
        @Override public String toString() { return "Search[query=REDACTED]"; }
    }
    public record Conversation(UUID id,UUID documentId,String filename,String title,Instant createdAt) {}
    public record ConversationPage(List<Conversation> items,int page,int size,long totalElements) {}
    public record Source(UUID documentId,String filename,UUID chunkId,int chunkIndex,Integer pageNumber,String quote) {
        @Override public String toString() { return "Source[chunkId="+chunkId+"]"; }
    }
    public record ImageItem(String title,String url,String thumbnailUrl,String sourceUrl,String source,String author,Integer width,Integer height) {}
    public record Turn(UUID id,UUID requestId,int index,String question,String answer,boolean found,
                       List<Source> sources,Instant createdAt,List<Attachment> attachments,List<ImageItem> images) {
        public Turn(UUID id,UUID requestId,int index,String question,String answer,boolean found,List<Source> sources,Instant createdAt,List<Attachment> attachments) {
            this(id,requestId,index,question,answer,found,sources,createdAt,attachments,List.of());
        }
        public Turn(UUID id,UUID requestId,int index,String question,String answer,boolean found,List<Source> sources,Instant createdAt) {
            this(id,requestId,index,question,answer,found,sources,createdAt,List.of(),List.of());
        }
        @Override public String toString() { return "Turn[id="+id+"]"; }
    }
    public record Attachment(UUID id,String filename,String mediaType,long size) {}
    public record TurnPage(List<Turn> items,int page,int size,long totalElements) {}
    public record AskResult(Turn turn,boolean created) {}
    public record Answer(String text,boolean found,List<Source> sources,List<ImageItem> images) {
        public Answer(String text,boolean found,List<Source> sources) {
            this(text,found,sources,List.of());
        }
        @Override public String toString() { return "Answer[found="+found+"]"; }
    }
}
