package com.emudhra.esign.cli;

public class CliReturnDocument {
    public int docId;
    public int status;
    public String errorCode;
    public String errorMessage;
    public String docInfo;
    public String docURL;
    public String documentHash;
    public String outputPath;      // set when signed PDF was written to a file
    public String signedDocument;  // base64, only when no outputPath was provided
    public String signedData;      // for HASH input type
    public String inputType;
}
