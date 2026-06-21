package com.emudhra.esign.cli;

import java.util.List;

public class CliResponse {
    public int status;
    public String errorCode;
    public String errorMessage;
    public String transactionId;
    public String preSignedTempFile;
    public String gatewayParameter;
    public String encryptedPath;
    public List<CliReturnDocument> documents;
}
