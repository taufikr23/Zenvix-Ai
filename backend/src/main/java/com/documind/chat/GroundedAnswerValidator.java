package com.documind.chat;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.springframework.stereotype.Component;
@Component
public class GroundedAnswerValidator {
    public static final String NOT_FOUND="Informasi tersebut tidak ditemukan dalam dokumen ini.";
    private final ObjectMapper mapper;
    public GroundedAnswerValidator(ObjectMapper mapper){this.mapper=mapper;}
    public ChatDtos.Answer noAnswer(){return new ChatDtos.Answer(NOT_FOUND,false,List.of());}
    public ChatDtos.Answer validate(String raw,List<RetrievalRepository.Hit> hits) {
        if(raw==null||raw.length()>12000)throw ChatErrors.invalidAnswer();
        JsonNode root;
        try{root=mapper.readTree(raw);}catch(Exception failure){throw ChatErrors.invalidAnswer();}
        if(root==null||!root.isObject()||!root.path("found").isBoolean()||!root.path("passages").isArray())
            throw ChatErrors.invalidAnswer();
        if(!root.get("found").asBoolean()){
            if(!root.get("passages").isEmpty())throw ChatErrors.invalidAnswer();
            if(root.path("answer").isTextual()&&!root.path("answer").asText().isBlank()){
                return new ChatDtos.Answer(root.get("answer").asText().strip(),false,List.of());
            }
            return noAnswer();
        }
        var passages=root.get("passages");
        if(passages.isEmpty()||passages.size()>3)throw ChatErrors.invalidAnswer();
        var sources=new ArrayList<ChatDtos.Source>();var unique=new HashSet<String>();
        for(var passage:passages){
            var sourceNode=passage.path("sourceId").isTextual()?passage.get("sourceId")
                    :(passage.path("source_id").isTextual()?passage.get("source_id"):passage.get("id"));
            if(sourceNode==null||!sourceNode.isTextual())throw ChatErrors.invalidAnswer();
            var quoteNode=passage.path("quote").isTextual()?passage.get("quote")
                    :(passage.path("content").isTextual()?passage.get("content"):passage.get("text"));
            if(quoteNode==null||!quoteNode.isTextual())throw ChatErrors.invalidAnswer();
            String label=sourceNode.asText(),quote=normalize(quoteNode.asText());
            int index;
            try{if(!label.matches("S[1-8]"))throw new IllegalArgumentException();index=Integer.parseInt(label.substring(1))-1;}
            catch(RuntimeException failure){throw ChatErrors.invalidAnswer();}
            if(index>=hits.size()||quote.length()<12||quote.length()>500||!unique.add(label))throw ChatErrors.invalidAnswer();
            var hit=hits.get(index);
            if(!normalize(hit.content()).contains(quote))throw ChatErrors.invalidAnswer();
            sources.add(new ChatDtos.Source(hit.documentId(),hit.filename(),hit.chunkId(),hit.chunkIndex(),hit.pageNumber(),quote));
        }
        if(root.path("answer").isTextual()&&!root.path("answer").asText().isBlank()){
            String ans=root.get("answer").asText().strip();
            if(!ans.contains("[1]")){
                ans=ans+" [1]";
            }
            return new ChatDtos.Answer(ans,true,List.copyOf(sources));
        }
        var answer=new StringBuilder("Berdasarkan dokumen:\n");
        for(int i=0;i<sources.size();i++)answer.append("\n").append(sources.get(i).quote()).append(" [").append(i+1).append("]\n");
        return new ChatDtos.Answer(answer.toString().strip(),true,List.copyOf(sources));
    }
    private String normalize(String text){return text.strip().replaceAll("\\s+"," ");}
}
