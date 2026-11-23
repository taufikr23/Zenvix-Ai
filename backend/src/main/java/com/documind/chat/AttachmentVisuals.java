package com.documind.chat;

import com.documind.common.ApiException;
import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.http.HttpStatus;
import org.w3c.dom.*;

/** Recover embedded page scans without changing or uploading the original file elsewhere. */
public final class AttachmentVisuals {
    private AttachmentVisuals() {}
    public static final String DOCX="application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    public record Visual(String mediaType,byte[] bytes) {
        @Override public String toString(){return "Visual[type="+mediaType+"]";}
    }
    public static List<Visual> read(String type,byte[] bytes){
        if(type.startsWith("image/")||type.equals("application/pdf"))return List.of(new Visual(type,bytes));
        if(!DOCX.equals(type))return List.of();
        try(var document=new XWPFDocument(new ByteArrayInputStream(bytes))){
            var ids=new LinkedHashSet<String>();
            collect(document.getDocument().getDomNode(),ids);
            var result=new ArrayList<Visual>();long total=0;
            for(String id:ids){
                var picture=document.getPictureDataByID(id);
                if(picture==null)throw invalid("Gambar tertaut dalam DOCX tidak tersedia. Simpan gambar di dalam dokumen lalu unggah kembali.");
                byte[] image=picture.getData();
                String mime=picture.getPackagePart().getContentType();
                if(!Set.of("image/png","image/jpeg").contains(mime))
                    throw invalid("Gambar dalam DOCX harus PNG atau JPG. Ekspor dokumen menjadi PDF untuk format gambar lainnya.");
                validateImage(image);
                total+=image.length;
                if(result.size()>=50||total>10L*1024*1024)throw invalid("Gambar dalam satu DOCX maksimal 50 gambar dan total 10 MB. Bagi dokumen menjadi beberapa bagian.");
                result.add(new Visual(mime,image));
            }
            return List.copyOf(result);
        }catch(ApiException failure){throw failure;}
        catch(Exception failure){throw invalid("Gambar dalam DOCX tidak dapat dibaca. Coba simpan ulang dokumen atau ekspor menjadi PDF.");}
    }
    private static void collect(Node node,Set<String> ids){
        if(node.getNodeType()==Node.ELEMENT_NODE){
            String local=node.getLocalName();
            if("blip".equals(local)||"imagedata".equals(local)){
                var attributes=node.getAttributes();
                for(int i=0;i<attributes.getLength();i++){
                    var attribute=attributes.item(i);
                    if(("embed".equals(attribute.getLocalName())||"id".equals(attribute.getLocalName()))&&attribute.getNamespaceURI()!=null
                            &&attribute.getNamespaceURI().endsWith("/relationships"))ids.add(attribute.getNodeValue());
                }
            }
        }
        for(Node child=node.getFirstChild();child!=null;child=child.getNextSibling())collect(child,ids);
    }
    private static void validateImage(byte[] bytes)throws IOException {
        try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){
            var readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext())throw invalid("Ada gambar rusak di dalam DOCX.");
            var reader=readers.next();
            try{
                reader.setInput(input);
                if((long)reader.getWidth(0)*reader.getHeight(0)>20_000_000)throw invalid("Resolusi setiap gambar dalam DOCX maksimal 20 megapiksel.");
            }finally{reader.dispose();}
        }
    }
    private static ApiException invalid(String message){return new ApiException(HttpStatus.BAD_REQUEST,"INVALID_ATTACHMENT",message);}
}
