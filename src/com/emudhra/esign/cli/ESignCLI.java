package com.emudhra.esign.cli;

import com.emudhra.esign.eSign;
import com.emudhra.esign.eSignInput;
import com.emudhra.esign.eSignServiceReturn;
import com.emudhra.esign.eSignSettings;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

public class ESignCLI {

    public static void main(String[] args) {
        // Redirect stderr so SDK log lines don't pollute stdout JSON
        System.setErr(new PrintStream(new OutputStream() {
            public void write(int b) {}
        }, true));

        // Read input JSON from file arg or stdin
        String json;
        try {
            if (args.length > 0) {
                json = new String(Files.readAllBytes(Paths.get(args[0])), StandardCharsets.UTF_8);
            } else {
                json = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            System.out.println(JsonUtil.errorJson("Failed to read input: " + e.getMessage()));
            System.exit(1);
            return;
        }

        // Parse JSON
        CliRequest request;
        try {
            request = JsonUtil.parseRequest(json);
            if (request == null) throw new IllegalArgumentException("Empty or null request");
        } catch (Exception e) {
            System.out.println(JsonUtil.errorJson("JSON parse error: " + e.getMessage()));
            System.exit(2);
            return;
        }

        // Merge configFile (request fields win on conflict)
        try {
            JsonUtil.mergeConfigFile(request);
        } catch (IOException e) {
            System.out.println(JsonUtil.errorJson("Failed to read configFile '" + request.configFile + "': " + e.getMessage()));
            System.exit(2);
            return;
        }

        // Apply defaults
        if (request.signatureContents == null) request.signatureContents = 21000;
        if (request.apiVersion == null)   request.apiVersion = "V2";
        if (request.authMode == null)     request.authMode = "OTP";
        if (request.maxWaitPeriod == null) request.maxWaitPeriod = 1440;
        if (request.signerId == null)     request.signerId = "";

        // Auto-generate transactionId
        if (request.transactionId == null || request.transactionId.isEmpty()) {
            request.transactionId = "TXN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        }

        // Auto-create tempFolder
        if (request.tempFolder != null && !request.tempFolder.isEmpty()) {
            try {
                Files.createDirectories(Paths.get(request.tempFolder));
            } catch (IOException e) {
                System.out.println(JsonUtil.errorJson("Cannot create tempFolder '" + request.tempFolder + "': " + e.getMessage()));
                System.exit(2);
                return;
            }
        }

        // Auto-detect command if not explicitly set
        if (request.command == null || request.command.isEmpty()) {
            request.command = detectCommand(request);
        }

        if (request.command == null) {
            System.out.println(JsonUtil.errorJson(
                "Cannot determine command — provide 'command' field, or include the required fields: " +
                "documents (for prepare/gateway_prepare), pkcs7Signatures (for append_signatures), " +
                "responseXml (for gateway_finalize), docBase64/docFilePath (for validate_pdf)"));
            System.exit(2);
            return;
        }

        try {
            System.out.println(dispatch(request));
        } catch (Exception e) {
            System.out.println(JsonUtil.errorJson("Unexpected error in command '" + request.command + "': " + e.getMessage()));
            System.exit(3);
        }
    }

    // -----------------------------------------------------------------------
    // Command auto-detection
    // -----------------------------------------------------------------------

    private static String detectCommand(CliRequest req) {
        if (req.pkcs7Signatures != null && !req.pkcs7Signatures.isEmpty())
            return "append_signatures";
        if (req.responseXml != null && !req.responseXml.isEmpty())
            return "gateway_finalize";
        if (req.documents != null && !req.documents.isEmpty() && req.gateway != null)
            return "gateway_prepare";
        if (req.documents != null && !req.documents.isEmpty())
            return "prepare";
        if (hasDocBase64(req))
            return "validate_pdf";
        if (req.path != null && !req.path.isEmpty())
            return "get_encrypted_path";
        if (req.transactionId != null && req.gateway != null)
            return "get_status";
        return null;
    }

    private static boolean hasDocBase64(CliRequest req) {
        if (req.docBase64 != null && !req.docBase64.isEmpty()) return true;
        if (req.documents != null && !req.documents.isEmpty()) {
            CliDocument d = req.documents.get(0);
            return (d.docBase64 != null && !d.docBase64.isEmpty())
                || (d.docFilePath != null && !d.docFilePath.isEmpty());
        }
        return false;
    }

    // -----------------------------------------------------------------------
    // Command dispatch
    // -----------------------------------------------------------------------

    private static String dispatch(CliRequest req) throws Exception {
        switch (req.command) {
            case "prepare":            return handlePrepare(req);
            case "append_signatures":  return handleAppendSignatures(req);
            case "gateway_prepare":    return handleGatewayPrepare(req);
            case "gateway_finalize":   return handleGatewayFinalize(req);
            case "validate_pdf":       return handleValidatePdf(req);
            case "get_status":         return handleGetStatus(req);
            case "get_encrypted_path": return handleGetEncryptedPath(req);
            default:
                return JsonUtil.errorJson("Unknown command: " + req.command);
        }
    }

    // -----------------------------------------------------------------------
    // Handlers
    // -----------------------------------------------------------------------

    private static String handlePrepare(CliRequest req) throws Exception {
        eSign sdk = buildVendorAgnostic(req);
        ArrayList<eSignInput> inputs = RequestMapper.toInputList(req.documents);
        eSignServiceReturn result = sdk.prepareDocuments(inputs, req.transactionId, req.tempFolder);
        return JsonUtil.toJson(ResponseMapper.fromServiceReturn(result, req.documents));
    }

    private static String handleAppendSignatures(CliRequest req) throws Exception {
        eSign sdk = buildVendorAgnostic(req);
        ArrayList<String> pkcs7List = new ArrayList<>(req.pkcs7Signatures);
        eSignServiceReturn result = sdk.appendSignatures(req.preSignedTempFile, pkcs7List);
        // Resolve output paths: prefer explicit outputPaths list, then documents array
        List<CliDocument> outputDocs = resolveOutputDocs(req);
        return JsonUtil.toJson(ResponseMapper.fromServiceReturn(result, outputDocs));
    }

    private static String handleGatewayPrepare(CliRequest req) throws Exception {
        eSign sdk = buildGateway(req);
        ArrayList<eSignInput> inputs = RequestMapper.toInputList(req.documents);
        eSign.eSignAPIVersion apiVer  = eSign.eSignAPIVersion.valueOf(req.apiVersion);
        eSign.AuthMode authMode       = eSign.AuthMode.valueOf(req.authMode);
        eSignServiceReturn result = sdk.getGatewayParameter(
            inputs, req.signerId, req.transactionId,
            req.responseUrl, req.redirectUrl, req.tempFolder,
            apiVer, authMode, req.maxWaitPeriod
        );
        return JsonUtil.toJson(ResponseMapper.fromServiceReturn(result, req.documents));
    }

    private static String handleGatewayFinalize(CliRequest req) throws Exception {
        // getSigedDocument makes no network call and never uses ASP/PFX credentials,
        // so gateway block is optional here
        eSign sdk = buildGatewayOrMinimal(req);
        eSignServiceReturn result = sdk.getSigedDocument(req.responseXml, req.preSignedTempFile);
        List<CliDocument> outputDocs = resolveOutputDocs(req);
        return JsonUtil.toJson(ResponseMapper.fromServiceReturn(result, outputDocs));
    }

    private static String handleValidatePdf(CliRequest req) throws Exception {
        eSign sdk = buildVendorAgnostic(req);
        String b64 = resolveDocBase64ForValidate(req);
        if (b64 == null || b64.isEmpty()) {
            return JsonUtil.errorJson("validate_pdf requires docBase64, docFilePath, or a documents entry");
        }
        eSignServiceReturn result = sdk.isValidPdf(b64);
        return JsonUtil.toJson(ResponseMapper.fromServiceReturn(result, null));
    }

    private static String handleGetStatus(CliRequest req) throws Exception {
        eSign sdk = buildGateway(req);
        eSignServiceReturn result = sdk.getStatus(req.transactionId);
        return JsonUtil.toJson(ResponseMapper.fromServiceReturn(result, null));
    }

    private static String handleGetEncryptedPath(CliRequest req) throws Exception {
        eSign sdk = buildGatewayOrMinimal(req);
        eSignServiceReturn result = sdk.getEncryptedPath(req.path);
        return JsonUtil.toJson(ResponseMapper.fromServiceReturn(result, null));
    }

    // -----------------------------------------------------------------------
    // eSign construction helpers
    // -----------------------------------------------------------------------

    private static eSign buildVendorAgnostic(CliRequest req) throws NoSuchAlgorithmException {
        if (req.proxy != null) {
            if (hasProxyAuth(req.proxy)) {
                return new eSign(null, null, null, null, null, null,
                    true, req.proxy.ip, req.proxy.port, 0,
                    eSignSettings.LogType.AllLog,
                    req.proxy.userId, req.proxy.password, null,
                    req.signatureContents);
            }
            return new eSign(req.signatureContents, true, req.proxy.ip, req.proxy.port);
        }
        return new eSign(req.signatureContents);
    }

    private static eSign buildGateway(CliRequest req) throws NoSuchAlgorithmException {
        CliGatewayConfig gw = req.gateway;
        if (req.proxy != null) {
            return new eSign(gw.aspId, gw.url, gw.urlV2, gw.pfxPath, gw.pfxPassword, gw.pfxAlias,
                true, req.proxy.ip, req.proxy.port, gw.sessionTimeout,
                eSignSettings.LogType.AllLog,
                hasProxyAuth(req.proxy) ? req.proxy.userId : null,
                hasProxyAuth(req.proxy) ? req.proxy.password : null,
                gw.pdfViewerLicence,
                req.signatureContents);
        }
        return new eSign(gw.aspId, gw.url, gw.urlV2, gw.pfxPath, gw.pfxPassword, gw.pfxAlias,
            false, "", 0, gw.sessionTimeout,
            eSignSettings.LogType.AllLog,
            null, null, gw.pdfViewerLicence,
            req.signatureContents);
    }

    private static eSign buildGatewayOrMinimal(CliRequest req) throws NoSuchAlgorithmException {
        return req.gateway != null ? buildGateway(req) : buildVendorAgnostic(req);
    }

    private static boolean hasProxyAuth(CliProxyConfig proxy) {
        return proxy.userId != null && !proxy.userId.isEmpty();
    }

    // -----------------------------------------------------------------------
    // Utilities
    // -----------------------------------------------------------------------

    /** Build a synthetic outputDocs list from outputPaths or documents array for phase-2 commands. */
    private static List<CliDocument> resolveOutputDocs(CliRequest req) {
        if (req.outputPaths != null && !req.outputPaths.isEmpty()) {
            List<CliDocument> list = new ArrayList<>();
            for (String path : req.outputPaths) {
                CliDocument d = new CliDocument();
                d.outputPath = path;
                list.add(d);
            }
            return list;
        }
        // documents array may contain just outputPath fields
        return req.documents;
    }

    private static String resolveDocBase64ForValidate(CliRequest req) throws IOException {
        if (req.docBase64 != null && !req.docBase64.isEmpty()) return req.docBase64;
        if (req.documents != null && !req.documents.isEmpty()) {
            CliDocument d = req.documents.get(0);
            if (d.docFilePath != null && !d.docFilePath.isEmpty()) {
                return Base64.getEncoder().encodeToString(Files.readAllBytes(Paths.get(d.docFilePath)));
            }
            if (d.docBase64 != null && !d.docBase64.isEmpty()) return d.docBase64;
        }
        return null;
    }
}
