package com.emudhra.esign.cli;

import com.emudhra.esign.ReturnDocument;
import com.emudhra.esign.eSignServiceReturn;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class ResponseMapper {

    /**
     * Maps the SDK result to a CliResponse.
     * outputDocs provides the outputPath per document (order-matched by docId).
     * If outputPath is set, the signed PDF is written to that file instead of
     * being returned as a base64 string in the JSON response.
     */
    public static CliResponse fromServiceReturn(eSignServiceReturn r, List<CliDocument> outputDocs) {
        CliResponse resp = new CliResponse();
        resp.status = r.getStatus();
        resp.errorCode     = nullIfEmpty(r.getErrorCode());
        resp.errorMessage  = nullIfEmpty(r.getErrorMessage());
        resp.transactionId = nullIfEmpty(r.getTransactionID());
        resp.preSignedTempFile = nullIfEmpty(r.getPreSignedTempFile());
        resp.gatewayParameter  = nullIfEmpty(r.getGatewayParameter());
        resp.encryptedPath     = nullIfEmpty(r.getEnCryptedPath());

        if (r.getReturnDocuments() != null) {
            resp.documents = new ArrayList<>();
            for (ReturnDocument rd : r.getReturnDocuments()) {
                CliReturnDocument crd = new CliReturnDocument();
                crd.docId        = rd.getDocId();
                // ReturnDocument success constructors never call setStatus(), so getStatus()==0
                // even for successful docs. Derive status from absence of errorCode instead.
                crd.errorCode    = nullIfEmpty(rd.getErrorCode());
                crd.errorMessage = nullIfEmpty(rd.getErrorMessage());
                crd.status       = (crd.errorCode == null && crd.errorMessage == null) ? 1 : 0;
                crd.docInfo      = nullIfEmpty(rd.getDocInfo());
                crd.docURL       = nullIfEmpty(rd.getDocURL());
                crd.documentHash = nullIfEmpty(rd.getDocumentHash());
                crd.signedData   = nullIfEmpty(rd.getSignedData());
                crd.inputType    = rd.getInputType() != null ? rd.getInputType().name() : null;

                String signedBase64 = rd.getSignedDocument();
                if (signedBase64 != null && !signedBase64.isEmpty()) {
                    String outPath = resolveOutputPath(outputDocs, rd.getDocId());
                    if (outPath != null && !outPath.isEmpty()) {
                        try {
                            byte[] pdfBytes = Base64.getDecoder().decode(signedBase64);
                            Files.write(Paths.get(outPath), pdfBytes);
                            crd.outputPath = outPath;
                        } catch (IOException e) {
                            crd.status = 0;
                            crd.errorMessage = "Failed to write output file '" + outPath + "': " + e.getMessage();
                        }
                    } else {
                        crd.signedDocument = signedBase64;
                    }
                }

                resp.documents.add(crd);
            }
        }
        return resp;
    }

    private static String resolveOutputPath(List<CliDocument> docs, int docId) {
        if (docs == null || docId < 0 || docId >= docs.size()) return null;
        return docs.get(docId).outputPath;
    }

    private static String nullIfEmpty(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }
}
