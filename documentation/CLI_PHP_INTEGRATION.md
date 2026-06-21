# PHP Integration Guide

Complete PHP examples for both signing flows.

## Helper Function

Add this once. All examples below use it.

```php
<?php

/**
 * Calls the eSign CLI with a JSON request and returns the decoded response.
 *
 * @param  array  $data    Request fields
 * @param  string $cliBin  Path to esign-cli.sh (or esign-cli.bat on Windows)
 * @return array           Decoded JSON response
 * @throws RuntimeException on CLI invocation failure
 */
function esignCli(array $data, string $cliBin = '/opt/esign/esign-cli.sh'): array {
    $reqFile = tempnam(sys_get_temp_dir(), 'esign_') . '.json';
    file_put_contents($reqFile, json_encode($data, JSON_UNESCAPED_UNICODE));

    $output = shell_exec(escapeshellarg($cliBin) . ' ' . escapeshellarg($reqFile));
    unlink($reqFile);

    if ($output === null || $output === '') {
        throw new RuntimeException('eSign CLI produced no output. Check that Java 11+ is on PATH.');
    }

    $result = json_decode($output, true);
    if ($result === null) {
        throw new RuntimeException('eSign CLI returned invalid JSON: ' . $output);
    }

    return $result;
}
```

> **Windows**: Change `$cliBin` default to `'C:\\esign\\esign-cli.bat'` and use `shell_exec()` normally — it works the same way.

---

## Flow A — Vendor-Agnostic

### Phase 1: Prepare

```php
$pdfPath = '/var/www/uploads/contract.pdf';
$outPath = '/var/www/signed/contract_' . time() . '.pdf';

$phase1 = esignCli([
    'tempFolder' => '/var/esign/temp',
    'documents'  => [[
        'docFilePath' => $pdfPath,
        'outputPath'  => $outPath,
        'signedBy'    => 'Ravi Kumar',
        'location'    => 'Bangalore',
        'reason'      => 'Approval of service agreement',
        'pageTobeSigned'          => 'Last',
        'coordinates'             => 'BottomRight',
        'patchSignatureAppearance' => true,
    ]],
]);

if ($phase1['status'] !== 1) {
    throw new RuntimeException('Prepare failed [' . $phase1['errorCode'] . ']: ' . $phase1['errorMessage']);
}

$documentHash = $phase1['documents'][0]['documentHash'];  // 64-char hex SHA-256
$tempFile     = $phase1['preSignedTempFile'];             // path to .sig file
```

### Call Your Signing Service

```php
/**
 * Example stub — replace with your actual HSM / TSA integration.
 * Input:  64-char hex SHA-256 hash
 * Output: Base64-encoded PKCS#7 / CMS signature
 */
function signWithHsm(string $hexHash): string {
    $response = file_get_contents('https://your-hsm.internal/sign', false,
        stream_context_create(['http' => [
            'method'  => 'POST',
            'header'  => 'Content-Type: application/json',
            'content' => json_encode(['hash' => $hexHash, 'algorithm' => 'SHA256']),
        ]])
    );
    return json_decode($response, true)['pkcs7'];  // Base64 string
}

$pkcs7 = signWithHsm($documentHash);
```

### Phase 2: Append Signatures

```php
$phase2 = esignCli([
    'preSignedTempFile' => $tempFile,
    'pkcs7Signatures'   => [$pkcs7],
]);

if ($phase2['status'] !== 1) {
    throw new RuntimeException('Sign failed [' . $phase2['errorCode'] . ']: ' . $phase2['errorMessage']);
}

$signedPath = $phase2['documents'][0]['outputPath'];
// Signed PDF is now at $signedPath on disk.
```

---

## Flow B — eMudhra Gateway (Aadhaar / PAN)

### Phase 1: Initiate Signing

```php
session_start();

$pdfPath = '/var/www/uploads/kyc.pdf';
$outPath = '/var/www/signed/kyc_' . session_id() . '.pdf';

$phase1 = esignCli([
    'configFile'  => '/etc/esign/gateway.json',
    'tempFolder'  => '/var/esign/temp',
    'responseUrl' => 'https://yourapp.com/esign/callback',
    'redirectUrl' => 'https://yourapp.com/esign/complete',
    'authMode'    => 'OTP',   // OTP | FingerPrint | IRIS | FaceRecognition
    'documents'   => [[
        'docFilePath' => $pdfPath,
        'outputPath'  => $outPath,
        'signedBy'    => $_SESSION['user_name'] ?? 'Applicant',
        'reason'      => 'Aadhaar eSign — identity verification',
        'pageTobeSigned' => 'Last',
        'coordinates'    => 'BottomRight',
    ]],
]);

if ($phase1['status'] !== 1) {
    throw new RuntimeException('Gateway prepare failed: ' . $phase1['errorMessage']);
}

// Store temp file in session — needed for Phase 2
$_SESSION['esign_temp_file']  = $phase1['preSignedTempFile'];
$_SESSION['esign_output_path'] = $outPath;

// Redirect user to eMudhra portal
$gatewayParameter = $phase1['gatewayParameter'];
$gatewayUrl = 'https://esigngateway.emudhra.com/eSignRequest';
```

```html
<!-- Auto-submit form to redirect user to eMudhra portal -->
<form id="gw" action="<?= htmlspecialchars($gatewayUrl) ?>" method="POST">
  <input type="hidden" name="eSignRequest" value="<?= htmlspecialchars($gatewayParameter) ?>">
</form>
<script>document.getElementById('gw').submit();</script>
```

### Phase 2: Callback Handler

```php
<?php
// File: /var/www/html/esign/callback.php
// eMudhra POSTs eSignResponse to this URL.

session_start();

$responseXml = $_POST['eSignResponse'] ?? null;
$tempFile    = $_SESSION['esign_temp_file'] ?? null;

if (!$responseXml || !$tempFile) {
    http_response_code(400);
    echo json_encode(['error' => 'Missing eSignResponse or session expired']);
    exit;
}

$phase2 = esignCli([
    'preSignedTempFile' => $tempFile,
    'responseXml'       => $responseXml,
]);

// Clean up session state
unset($_SESSION['esign_temp_file'], $_SESSION['esign_output_path']);

// Clean up .sig temp file
if (file_exists($tempFile)) {
    unlink($tempFile);
}

if ($phase2['status'] !== 1) {
    error_log('eSign finalize failed: ' . $phase2['errorMessage']);
    header('Location: /esign/error?code=' . urlencode($phase2['errorCode'] ?? 'UNKNOWN'));
    exit;
}

$signedPath = $phase2['documents'][0]['outputPath'];
// Signed PDF is now at $signedPath.

header('Location: /esign/complete?file=' . urlencode(basename($signedPath)));
```

---

## Multi-Document Signing

```php
$docs = [
    ['path' => '/uploads/doc1.pdf', 'out' => '/signed/doc1.pdf', 'reason' => 'Contract part 1'],
    ['path' => '/uploads/doc2.pdf', 'out' => '/signed/doc2.pdf', 'reason' => 'Contract part 2'],
    ['path' => '/uploads/doc3.pdf', 'out' => '/signed/doc3.pdf', 'reason' => 'Contract part 3'],
];

$documents = array_map(fn($d) => [
    'docFilePath' => $d['path'],
    'outputPath'  => $d['out'],
    'signedBy'    => 'Ravi Kumar',
    'reason'      => $d['reason'],
    'pageTobeSigned' => 'Last',
    'coordinates'    => 'BottomRight',
], $docs);

$phase1 = esignCli(['tempFolder' => '/var/esign/temp', 'documents' => $documents]);
if ($phase1['status'] !== 1) { throw new RuntimeException($phase1['errorMessage']); }

// Collect hashes (order matches $documents array)
$hashes   = array_column($phase1['documents'], 'documentHash');
$tempFile = $phase1['preSignedTempFile'];

// Sign each hash — one PKCS#7 per document, same order
$pkcs7List = array_map('signWithHsm', $hashes);

$phase2 = esignCli([
    'preSignedTempFile' => $tempFile,
    'pkcs7Signatures'   => $pkcs7List,
]);

foreach ($phase2['documents'] as $doc) {
    if ($doc['status'] === 1) {
        echo "Signed: " . $doc['outputPath'] . PHP_EOL;
    } else {
        echo "Failed doc {$doc['docId']}: " . $doc['errorMessage'] . PHP_EOL;
    }
}
```

---

## Signature with Image

```php
esignCli([
    'tempFolder' => '/var/esign/temp',
    'documents'  => [[
        'docFilePath'        => '/uploads/doc.pdf',
        'outputPath'         => '/signed/doc.pdf',
        'signedBy'           => 'Ravi Kumar',
        'signatureImagePath' => '/assets/signatures/ravi_kumar.png',
        'appearanceType'     => 'StandardSignature',
        'pageTobeSigned'     => 'Last',
        'coordinates'        => 'BottomRight',
    ]],
]);
```

---

## Place Signature at Exact Page Coordinates

```php
esignCli([
    'tempFolder' => '/var/esign/temp',
    'documents'  => [[
        'docFilePath'          => '/uploads/form.pdf',
        'outputPath'           => '/signed/form.pdf',
        'signedBy'             => 'Ravi Kumar',
        'pageTobeSigned'       => 'PageLevel',
        'pageLevelCoordinates' => '2-400,60,530,130',  // page 2, (x1,y1,x2,y2) in PDF points
    ]],
]);
```

---

## Error Handling Pattern

```php
function signDocument(string $pdfPath, string $outPath, callable $hsmFn): string {
    $phase1 = esignCli(['tempFolder' => '/var/esign/temp', 'documents' => [[
        'docFilePath' => $pdfPath,
        'outputPath'  => $outPath,
        'signedBy'    => 'Ravi Kumar',
        'reason'      => 'Document approval',
    ]]]);

    if ($phase1['status'] !== 1) {
        throw new RuntimeException(sprintf(
            'eSign prepare error [%s]: %s',
            $phase1['errorCode'] ?? 'N/A',
            $phase1['errorMessage'] ?? 'unknown'
        ));
    }

    // Check per-document status too
    foreach ($phase1['documents'] as $doc) {
        if ($doc['status'] !== 1) {
            throw new RuntimeException(sprintf(
                'Document %d prepare error [%s]: %s',
                $doc['docId'],
                $doc['errorCode'] ?? 'N/A',
                $doc['errorMessage'] ?? 'unknown'
            ));
        }
    }

    $pkcs7 = $hsmFn($phase1['documents'][0]['documentHash']);

    $phase2 = esignCli([
        'preSignedTempFile' => $phase1['preSignedTempFile'],
        'pkcs7Signatures'   => [$pkcs7],
    ]);

    if ($phase2['status'] !== 1) {
        throw new RuntimeException(sprintf(
            'eSign append error [%s]: %s',
            $phase2['errorCode'] ?? 'N/A',
            $phase2['errorMessage'] ?? 'unknown'
        ));
    }

    return $phase2['documents'][0]['outputPath'];
}
```
