// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.example.album;

import com.yahoo.docproc.DocumentProcessor;
import com.yahoo.docproc.Processing;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentOperation;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.idstring.IdString;
import com.yahoo.documentapi.AsyncParameters;
import com.yahoo.documentapi.AsyncSession;
import com.yahoo.documentapi.DocumentAccess;
import com.yahoo.documentapi.DocumentResponse;
import com.yahoo.documentapi.Response;
import com.yahoo.documentapi.ResponseHandler;
import com.yahoo.documentapi.Result;

import java.util.HashMap;
import java.util.Map;

public class LyricsDocumentProcessor extends DocumentProcessor {

    private final static String MUSIC_DOCUMENT_TYPE = "music";
    private final static String LYRICS_DOCUMENT_TYPE = "lyrics";
    private static final String VAR_LYRICS = "lyrics";
    private static final String VAR_REQ_ID = "reqId";
    private final Map<Long, Processing> processings = new HashMap<>();

    private final DocumentAccess access = DocumentAccess.createDefault();
    private final AsyncSession  session = access.createAsyncSession(new AsyncParameters().setResponseHandler(new RespHandler()));

    class RespHandler implements ResponseHandler {
        @Override
        public void handleResponse(Response response) {
            System.out.println("In handleResponse");
            if (response.isSuccess()){
                long reqId = response.getRequestId();
                System.out.println("  requestID: " + reqId);
                if (response instanceof DocumentResponse) {
                    Processing processing = processings.remove(reqId);
                    processing.removeVariable(VAR_REQ_ID);
                    DocumentResponse resp = (DocumentResponse)response;
                    Document doc = resp.getDocument();
                    if (doc != null) {
                        System.out.println("  Found lyrics for : " + doc.toString());
                        processing.setVariable(VAR_LYRICS, resp.getDocument().getFieldValue("song_lyrics"));
                    }
                }
            }
        }
    }

    @Override
    public Progress process(Processing processing) {
        System.out.println("In process");
        for (DocumentOperation op : processing.getDocumentOperations()) {
            if (op instanceof DocumentPut) {
                DocumentPut put = (DocumentPut) op;
                Document document = put.getDocument();
                if (document.getDataType().isA(MUSIC_DOCUMENT_TYPE)) {

                    // Set lyrics if already looked up, and set DONE
                    FieldValue lyrics = (FieldValue)processing.getVariable(VAR_LYRICS);
                    if (lyrics != null) {
                        document.setFieldValue("lyrics", lyrics);
                        System.out.println("  Set lyrics, Progress.DONE");
                        return DocumentProcessor.Progress.DONE;
                    }

                    // Make a lyric request if not already pending
                    if (processing.getVariable(VAR_REQ_ID) == null) {
                        String[] parts = document.getId().toString().split(":");
                        Result res = session.get(new DocumentId("id:mynamespace:" + LYRICS_DOCUMENT_TYPE + "::" + parts[parts.length-1]));
                        if (res.isSuccess()) {
                            processing.setVariable(VAR_REQ_ID, res.getRequestId());
                            processings.put(res.getRequestId(), processing);
                            System.out.println("  Added to requests pending: " + res.getRequestId());
                        }
                    }
                    System.out.println("  Request pending ID: " + (long)processing.getVariable(VAR_REQ_ID) + ", Progress.LATER");
                    return DocumentProcessor.Progress.LATER;
                }
            }
        }
        return DocumentProcessor.Progress.DONE;
    }

    @Override
    public void deconstruct() {
        super.deconstruct();
        session.destroy();
        access.shutdown();
    }
}
