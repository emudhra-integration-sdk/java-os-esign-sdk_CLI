# eSign CLI — Shell Wrapper Guide

The CLI wrapper lets any language (PHP, Node, Python, Ruby, etc.) use the eSign Java SDK by calling it as a subprocess. PDFs are passed as file paths, never as inline base64 — keeping the JSON command small regardless of PDF size.

## Requirements

- Java 11 or later (`java -version` to check)
- Built JAR: `dist/eSignASPLibrary5_10.jar` (includes CLI classes)
- `lib/gson-2.10.1.jar` (bundled in repo)

## Quick start

```sh
# Linux / macOS
chmod +x esign-cli.sh
echo '{"docBase64":"JVBERi0x..."}' | ./esign-cli.sh

# Windows
esign-cli.bat request.json
```

The CLI accepts a JSON request either as a file path argument or on stdin, and prints a JSON response to stdout. Stderr is suppressed so stdout is always clean JSON.

---

## Two Signing Flows

### Flow A — Vendor-Agnostic

Use with any HSM, TSA, or certificate authority.

```
Phase 1 (prepare)          CLI reads PDF → computes SHA-256 hash → writes .sig temp file
                           Response: { documentHash, preSignedTempFile }

Your code                  Send documentHash to your HSM → receive PKCS#7 signature blob

Phase 2 (append_signatures) CLI reads .sig + PKCS#7 → injects signature → writes signed PDF
                           Response: { outputPath }
```

### Flow B — eMudhra Gateway (Aadhaar / PAN)

```
Phase 1 (gateway_prepare)  CLI calls eMudhra gateway → writes .sig temp file
                           Response: { gatewayParameter, preSignedTempFile }

Your code                  POST gatewayParameter to eMudhra portal
                           User authenticates (OTP / biometric)
                           eMudhra POSTs eSignResponse XML to your responseUrl

Phase 2 (gateway_finalize) CLI reads .sig + responseXml → injects signature → writes signed PDF
                           Response: { outputPath }
                           (No network call, no gateway credentials needed in Phase 2)
```

---

## Config File

For gateway flow, write ASP credentials to a file once:

```json
{
  "signatureContents": 21000,
  "gateway": {
    "aspId":       "YOUR_ASP_ID",
    "url":         "https://esigngateway.emudhra.com/eSignRequest",
    "urlV2":       "https://esigngateway.emudhra.com/v2/eSignRequest",
    "pfxPath":     "/etc/esign/certs/asp.pfx",
    "pfxPassword": "your-pfx-password",
    "pfxAlias":    "asp_cert"
  }
}
```

Reference it in every call via `"configFile": "/etc/esign/gateway.json"`. Per-call fields override config-file fields when both are present.

---

## Vendor-Agnostic Flow — Examples

### Phase 1 Request

```json
{
  "tempFolder": "/var/esign/temp",
  "documents": [
    {
      "docFilePath": "/uploads/contract.pdf",
      "outputPath":  "/signed/contract_signed.pdf",
      "signedBy":    "Ravi Kumar",
      "location":    "Bangalore",
      "reason":      "Approval of contract terms",
      "pageTobeSigned": "Last",
      "coordinates":    "BottomRight",
      "patchSignatureAppearance": true
    }
  ]
}
```

### Phase 1 Response

```json
{
  "status": 1,
  "transactionId": "TXN-A3F2C8D1E5B7F019",
  "preSignedTempFile": "/var/esign/temp/TXN-A3F2C8D1E5B7F019.sig",
  "documents": [
    {
      "docId": 0,
      "status": 1,
      "documentHash": "3a7f9c2dbe4f1a8e6c0d5b9f2e7a4c1d8b3f6e9a2c5d8e1b4f7a0c3d6e9b2f5"
    }
  ]
}
```

### Phase 2 Request

```json
{
  "preSignedTempFile": "/var/esign/temp/TXN-A3F2C8D1E5B7F019.sig",
  "pkcs7Signatures": [
    "MIIEzgYJKoZIhvcNAQcCoIIEvzCCBLsCAQExDzANBglghkgBZQMEAgEFADALBgkq..."
  ]
}
```

### Phase 2 Response

```json
{
  "status": 1,
  "documents": [
    {
      "docId": 0,
      "status": 1,
      "outputPath": "/signed/contract_signed.pdf"
    }
  ]
}
```

---

## eMudhra Gateway Flow — Examples

### Phase 1 Request

```json
{
  "configFile":  "/etc/esign/gateway.json",
  "tempFolder":  "/var/esign/temp",
  "responseUrl": "https://yourapp.com/esign/callback",
  "redirectUrl": "https://yourapp.com/esign/redirect",
  "authMode":    "OTP",
  "documents": [
    {
      "docFilePath": "/uploads/kyc.pdf",
      "outputPath":  "/signed/kyc_signed.pdf",
      "signedBy":    "Applicant Name",
      "reason":      "Aadhaar identity verification",
      "pageTobeSigned": "Last",
      "coordinates":    "BottomRight"
    }
  ]
}
```

### Phase 1 Response (includes `gatewayParameter`)

```json
{
  "status": 1,
  "transactionId": "TXN-B9D1E3F7A2C4D8E1",
  "preSignedTempFile": "/var/esign/temp/TXN-B9D1E3F7A2C4D8E1.sig",
  "gatewayParameter": "<eSignRequest ...>...</eSignRequest>",
  "documents": [
    { "docId": 0, "status": 1, "documentHash": "7c2e..." }
  ]
}
```

### Phase 2 Request (no credentials needed)

```json
{
  "preSignedTempFile": "/var/esign/temp/TXN-B9D1E3F7A2C4D8E1.sig",
  "responseXml": "<EsignResp ...>...</EsignResp>"
}
```

---

## Multi-Document Signing

Sign up to 5 PDFs in a single Phase 1 call. Provide one `pkcs7Signatures` entry per document in Phase 2, order-matched.

```json
{
  "tempFolder": "/var/esign/temp",
  "documents": [
    {
      "docFilePath": "/uploads/doc1.pdf",
      "outputPath":  "/signed/doc1_signed.pdf",
      "signedBy": "Ravi Kumar",
      "reason": "Batch signing — document 1"
    },
    {
      "docFilePath": "/uploads/doc2.pdf",
      "outputPath":  "/signed/doc2_signed.pdf",
      "signedBy": "Ravi Kumar",
      "reason": "Batch signing — document 2"
    }
  ]
}
```

Phase 2 for multi-doc:

```json
{
  "preSignedTempFile": "/var/esign/temp/TXN-....sig",
  "pkcs7Signatures": [
    "MIIEzg...==",
    "MIIFzg...=="
  ]
}
```

---

## Signature Placement Options

| `pageTobeSigned` | `coordinates`   | Result                                         |
|------------------|-----------------|------------------------------------------------|
| `Last` (default) | `BottomRight`   | Bottom-right of the last page                  |
| `Last`           | `TopLeft`       | Top-left of the last page                      |
| `All`            | `BottomRight`   | Bottom-right of every page                     |
| `First`          | `CenterMiddle`  | Center of the first page                       |
| `PageLevel`      | _(not used)_    | Exact position via `pageLevelCoordinates`       |

For `PageLevel`, set `pageLevelCoordinates` as `"page-x1,y1,x2,y2"`:

```json
{
  "pageTobeSigned": "PageLevel",
  "pageLevelCoordinates": "3-100,50,250,120"
}
```

All other placement modes auto-fill `pageLevelCoordinates` internally — you do not need to provide it.

---

## Signature Image

Provide a file path (preferred) or inline base64:

```json
{
  "docFilePath": "/uploads/doc.pdf",
  "signatureImagePath": "/assets/signatures/ravi_kumar.png",
  "appearanceType": "StandardSignature"
}
```

---

## Output: File vs Base64

If `outputPath` is set, the signed PDF is written to disk and the response includes only the path:

```json
{ "outputPath": "/signed/doc_signed.pdf", "signedDocument": null }
```

If `outputPath` is omitted, the signed PDF is returned as Base64 in `signedDocument`. Not recommended for large PDFs.

---

## Request Field Reference

### Top-level

| Field               | Type     | Default        | Description                                                   |
|---------------------|----------|----------------|---------------------------------------------------------------|
| `configFile`        | string   | —              | Path to gateway JSON; merged before processing                |
| `tempFolder`        | string   | auto-created   | Where `.sig` temp files are written                           |
| `transactionId`     | string   | UUID generated | Auto-generated as `TXN-{16 hex}` if omitted                  |
| `signatureContents` | int      | 21000          | Bytes reserved for PKCS#7 in the PDF byte range               |
| `preSignedTempFile` | string   | —              | Required for Phase 2. Path from Phase 1 response              |
| `pkcs7Signatures`   | string[] | —              | Base64 PKCS#7, one per doc, order-matched to prepare          |
| `responseXml`       | string   | —              | eMudhra callback XML. Triggers `gateway_finalize`             |
| `responseUrl`       | string   | —              | Your callback URL for eMudhra POST                            |
| `redirectUrl`       | string   | —              | Browser redirect URL after signing portal closes              |
| `apiVersion`        | string   | `V2`           | `V2` = Aadhaar; `V3` = PAN                                   |
| `authMode`          | string   | `OTP`          | `OTP` · `FingerPrint` · `IRIS` · `FaceRecognition`           |
| `maxWaitPeriod`     | int      | 1440           | Gateway session expiry in minutes                             |
| `gateway`           | object   | —              | `{aspId, url, urlV2, pfxPath, pfxPassword, pfxAlias}`        |
| `proxy`             | object   | —              | `{ip, port, userId, password}`                                |

### Per-document (inside `documents[]`)

| Field                       | Description                                                                 |
|-----------------------------|-----------------------------------------------------------------------------|
| `docFilePath`               | Path to input PDF (primary)                                                 |
| `outputPath`                | Where signed PDF is written after Phase 2                                   |
| `signedBy`                  | Signer name in the appearance field                                         |
| `location`                  | City/location in the appearance field                                       |
| `reason`                    | Reason for signing                                                          |
| `pageTobeSigned`            | `Last` (default) · `First` · `All` · `PageLevel`                          |
| `coordinates`               | `BottomRight` (default) · `TopLeft` · `TopMiddle` · `TopRight` · `CenterLeft` · `CenterMiddle` · `CenterRight` · `BottomLeft` · `BottomMiddle` |
| `pageLevelCoordinates`      | For PageLevel mode: `"page-x1,y1,x2,y2"`. Auto-filled for named coordinates. |
| `appearanceType`            | `StandardSignature` (default) · `GraphicOnly` · `TextOnly`                |
| `signatureImagePath`        | File path to PNG/JPG signature image                                        |
| `coSign`                    | `true` (default) — allow multiple signatures on the same PDF               |
| `patchSignatureAppearance`  | `true` — patch visual appearance after signing (recommended)               |
| `contentSearch`             | `{searchText, height, width, offset, position}` — sign at a text anchor   |
| `advanceSignature`          | `{imagePath, imageType, leftSideText, rightSideText}`                      |
| `customStyle`               | `{border, fontSize, fontColor:{r,g,b}}`                                    |
| `docBase64`                 | Fallback inline PDF (not recommended for large files)                       |
| `docHash`                   | Pre-computed hash for HASH input mode                                       |
| `pdfPassword`               | Password to open a protected PDF                                            |

---

## Response Schema

```json
{
  "status": 1,
  "errorCode": null,
  "errorMessage": null,
  "transactionId": "TXN-...",
  "preSignedTempFile": "/var/esign/temp/TXN-....sig",
  "gatewayParameter": null,
  "encryptedPath": null,
  "documents": [
    {
      "docId": 0,
      "status": 1,
      "errorCode": null,
      "errorMessage": null,
      "documentHash": "64-char hex (Phase 1 only)",
      "outputPath": "/signed/doc_signed.pdf",
      "signedDocument": null
    }
  ]
}
```

**Status semantics:** `status: 1` = success, `status: 0` = failure. Check both top-level and per-document status in multi-document calls.

---

## Auto-Detection Logic

The CLI infers the command from the request shape — no `command` field needed:

```
pkcs7Signatures present        → append_signatures
responseXml present            → gateway_finalize
documents + gateway block      → gateway_prepare
documents present              → prepare
docBase64 at top level         → validate_pdf
transactionId + gateway block  → get_status
path present                   → get_encrypted_path
```

---

## Error Handling

All errors return `status: 0` with `errorCode` and `errorMessage` populated. The CLI always exits with status 0 and returns valid JSON — it never throws a raw exception to stdout.

Common error codes:

| Code    | Description                                                              |
|---------|--------------------------------------------------------------------------|
| ESS-101 | Invalid or missing PDF                                                   |
| ESS-103 | Temp folder path is empty or inaccessible                                |
| ESS-120 | Unable to find content for content-search based placement                |
| ESS-201 | Gateway connection error                                                 |
| ESS-301 | Invalid or expired `.sig` temp file                                      |

For gateway errors, the error message from eMudhra's response is passed through verbatim.

---

## Temp File Management

Phase 1 writes a `.sig` temp file to `tempFolder`. This file:
- Contains all state needed for Phase 2 (PDF byte-range, transaction ID, document list)
- Is safe to store on disk between phases — gateway flows may have a user wait of several minutes
- Should be deleted after Phase 2 completes successfully
- Should be deleted if Phase 2 fails unrecoverably (e.g., user abandoned the eMudhra portal)

The temp file path follows the pattern: `{tempFolder}/{transactionId}.sig`
