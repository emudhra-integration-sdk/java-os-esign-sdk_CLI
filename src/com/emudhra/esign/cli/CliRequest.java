package com.emudhra.esign.cli;

import java.util.List;

public class CliRequest {

    // Command — auto-detected if absent
    public String command;

    // Config file — merged before processing; per-request fields win on conflict
    public String configFile;

    // SDK constructor params
    public Integer signatureContents;

    // Transaction / temp storage
    public String transactionId;
    public String tempFolder;
    public String preSignedTempFile;

    // Phase 2 inputs
    public List<String> pkcs7Signatures;
    public String responseXml;

    // Output paths for phase 2 (order-matched to documents used in phase 1)
    public List<String> outputPaths;

    // Gateway phase 1 params
    public String signerId;
    public String responseUrl;
    public String redirectUrl;
    public String apiVersion;
    public String authMode;
    public Integer maxWaitPeriod;

    // Utility commands
    public String docBase64;  // for validate_pdf when not using documents array
    public String path;       // for get_encrypted_path

    // Credentials
    public CliGatewayConfig gateway;
    public CliProxyConfig proxy;

    // Documents (phase 1 and validate_pdf)
    public List<CliDocument> documents;
}
