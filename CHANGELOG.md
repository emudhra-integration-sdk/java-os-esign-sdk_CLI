# Changelog

All notable changes to this project are documented here.
Versions follow [Semantic Versioning](https://semver.org/).

---

## [v1.0.0] — 2026-06-22

### Added

#### CLI Wrapper
- New package `com.emudhra.esign.cli` — wraps the Java SDK for subprocess invocation from any language
- `ESignCLI.java` — main entry point; reads JSON from a file argument or stdin, writes JSON to stdout
- `esign-cli.sh` / `esign-cli.bat` — launcher scripts for Linux/macOS and Windows
- Command auto-detection from request shape — no `command` field required
- Auto-generation of `transactionId` as `TXN-{16 hex chars uppercase}` when omitted
- Auto-creation of `tempFolder` with `Files.createDirectories()` when absent
- `configFile` support — write gateway credentials once, reference by path in every call

#### Signing Flows
- **Vendor-Agnostic** (`prepare` → external HSM/TSA → `append_signatures`)
  - Phase 1 computes SHA-256 hash and writes serialized state to a `.sig` temp file
  - Phase 2 injects the PKCS#7 signature into the pre-reserved byte-range slot
- **eMudhra Gateway** (`gateway_prepare` → Aadhaar/PAN auth → `gateway_finalize`)
  - Phase 1 calls eMudhra's gateway and returns `gatewayParameter` for portal redirect
  - Phase 2 works offline — no gateway credentials or network call needed to finalize

#### Input / Output
- PDF input by file path (`docFilePath`) — no base64 blobs in the JSON command
- Signed PDF written to disk (`outputPath`) — omit to fall back to base64 in response
- Signature image by file path (`signatureImagePath`) — preferred over inline base64
- Multi-document signing — up to 5 PDFs in one CLI call

#### SDK Quirk Workarounds
- `pageLevelCoordinates` auto-fill for named coordinate modes (`BottomRight`, `TopLeft`, etc.) —
  the SDK requires this field non-empty even when actual placement comes from the `Coordinates` enum
- Per-document `status` derived from absence of `errorCode` — the SDK's `ReturnDocument`
  success constructor never calls `setStatus()`, so `getStatus()` always returns `0` for
  successfully-processed documents

#### Dependencies
- Added `gson-2.10.1.jar` (~240 KB, Apache 2.0) for JSON parsing

#### Documentation
- `documentation/CLI_GUIDE.md` — full reference: both flows, field tables, placement options,
  response schema, auto-detection logic, error codes, temp file lifecycle
- `documentation/CLI_PHP_INTEGRATION.md` — complete PHP examples: helper function,
  vendor-agnostic flow, gateway flow with callback handler, multi-doc, error handling pattern
- Updated `README.md` with CLI wrapper banner

#### CI / CD
- `.github/workflows/release.yml` — builds JAR on tag push and attaches it to the GitHub Release
