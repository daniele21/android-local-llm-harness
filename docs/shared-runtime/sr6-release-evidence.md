# SR-6 shared-runtime release evidence

Status: active
Document type: evidence-runbook
Owner: shared-runtime-validation
Canonical scope: shared-runtime.sr6-release-evidence
Read when: preparing or reviewing the physical/two-APK release gate
Last reviewed: 2026-09-05

## Goal

SR-6 validates the exact deployment boundary being claimed for a consumer: a separately installed Harnex Host, a packaged Consumer SDK, Android Binder caller authorization and the real signing topology of the participating applications.

ADR 0017 supersedes the original assumption that every production consumer shares the Host signing identity. Harnex and external consumers such as RedactGuard may use distinct Play App Signing identities. The manifest `BIND_LOCAL_LLM` permission is only a bind capability; Binder UID/package/signer verification plus explicit Harnex Control Plane authorization is the trust boundary.

Deterministic emulator E2E can prove the Binder authorization state machine with distinct ephemeral identities. Physical/Play evidence remains required when the claim depends on actual Play App Signing identity, representative hardware or real GGUF/runtime behavior.

## Evidence paths

SR-6 has two complementary paths rather than one universal signing fixture.

### Packaged same-publisher fixture

Use:

```bash
bash scripts/capture-shared-runtime-release-evidence.sh --device <adb-serial>
```

This path validates packaged release AAR consumption, real Binder/process behavior and real runtime/device evidence using the repository-owned consumer fixture. The positive Host and fixture may intentionally share the external phone-test signing configuration because this path is a same-publisher fixture, not proof of independent-consumer compatibility.

The runner requires a physical `arm64-v8a` device by default. `--allow-emulator` is available only for explicitly labelled preflight evidence.

The positive Host and fixture use the existing external phone-test signing configuration:

```text
LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_STORE_FILE
LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_STORE_PASSWORD
LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_KEY_ALIAS
LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_KEY_PASSWORD
```

On macOS the runner may reuse the same Keychain entry documented by `scripts/build-phone-test-release.sh`. Signing secrets are never written to the evidence directory.

### Independently signed consumer path

For RedactGuard or another separately distributed consumer, exact-head automation must:

1. build the exact Harnex Host and Consumer SDK candidate;
2. build the exact consumer against that SDK candidate;
3. sign Host/Host-owned test APKs with identity A and consumer/consumer-test APKs with identity B;
4. prove A != B from APK certificate digests;
5. install both applications;
6. prove the consumer is denied while its observed identity is pending;
7. use Host-owned instrumentation/UI authority to authorize the exact observed package + signer;
8. prove authorized connect -> disconnect -> reconnect;
9. preserve bounded source/signing/result evidence and delete ephemeral private keys.

For a Play-distributed candidate, physical confirmation installs the actual Internal Testing builds and records the Host and consumer Play App Signing digest identities. Local ephemeral signing is not presented as evidence of the real Play identity.

## Model setup for packaged physical evidence

The physical runner never downloads, copies or discovers a host-private GGUF path. The release Host must already have a curated Qwen3.5 model installed and selected.

For first-time setup:

```bash
bash scripts/capture-shared-runtime-release-evidence.sh \
  --device <adb-serial> \
  --host-only
```

This builds, installs and launches the signed release Host while preserving its app-private state. Install/select the curated model through the Host UI, then rerun the normal evidence command.

## Packaged fixture positive flow

The runner builds:

- `apps/local-llm-phone-test` release APK;
- `transports/android-binder-client` release AAR;
- `transports/android-binder-contract` release AAR;
- `apps/shared-runtime-client-consumer-fixture` release APK and instrumentation APK.

The consumer fixture references the packaged AARs rather than project-source client classes. For the intentional same-publisher positive path, the runner verifies that Host, fixture and instrumentation APKs have the expected common SHA-256 signing-certificate digest.

`SharedRuntimeReleaseEvidenceTest` then verifies over the real Binder/process boundary:

- bind and registration;
- prepare against the Host-selected curated model;
- session creation;
- streamed generation and successful completion;
- privacy-safe TTFT/total/token/throughput markers;
- active generation cancellation and cancellation latency;
- session close;
- Host process termination, typed `CONNECTION_LOST`, Host restart and clean reconnect.

The instrumentation test does not print prompt or generated output content.

## Repository authorization review

The current security boundary is deliberately stronger than possession of an Android permission:

- the exported Host service requires the variant-specific normal `BIND_LOCAL_LLM` capability permission;
- Host authorization captures Binder calling UID before dispatch and resolves the exact installed package;
- empty or ambiguous UID/package mappings fail closed;
- the installed package signing certificate must match the exact live Host policy;
- for independently signed consumers, live policy comes from explicitly authorized Harnex Control Plane state for the source-observed package + signer;
- newly observed independent consumers are `PENDING` and denied;
- signing identity replacement is `SIGNATURE_CHANGED` and denied until explicit reauthorization;
- the Host owns `ApplicationId`, allowed `UseCaseId` and model binding rather than accepting client-selected authority;
- emulator fault/control and broader diagnostics remain separate from inference binding.

Same-publisher fixtures can retain reviewed same-signer policy as an intentional test topology. They do not weaken or replace the independent-consumer path.

## Negative signer evidence

The packaged fixture runner may still create a short-lived PKCS12 identity and rebuild only the fixture to prove that a mismatched signer cannot inherit a same-publisher allowlist entry. The expected result remains `PERMISSION_DENIED`, now due to Binder caller policy rather than a signature-level manifest permission.

The independent-consumer path also requires denial before explicit authorization. A known package with a distinct source-observed signer does not become authorized merely because it declares `BIND_LOCAL_LLM` or is installed.

Temporary keystores/passwords are deleted when runners exit and are never committed or copied into evidence bundles.

## Evidence bundle

The packaged physical runner keeps its existing default output:

```text
build/shared-runtime-evidence/<UTC timestamp>/
build/shared-runtime-evidence/<UTC timestamp>.tar.gz
```

Depending on the scenario, evidence includes:

- exact Harnex and consumer source revision identities;
- Host/consumer package and version/build identities;
- Host/consumer signing-certificate SHA-256 digest identities;
- Consumer SDK and Binder protocol identity;
- Harnex authorization transition markers for independent-consumer evidence;
- device manufacturer/model/Android/ABI for physical evidence, excluding adb serial;
- functional/cancellation/process-death/reconnect results where tested;
- privacy-safe timing/token/memory/thermal markers where real runtime/device evidence is tested;
- APK hashes and a privacy/readme summary.

Evidence excludes:

- prompts and generated output;
- GGUF bytes and Host-private model paths;
- Binder client tokens;
- adb serial numbers;
- keystores, signing passwords and full certificates.

## Acceptance

An evidence record is reviewable only when its scope matches the claim.

For an independently signed consumer authorization claim:

1. exact Harnex and consumer source revisions are recorded;
2. Host and consumer APK signing digests are present and distinct;
3. the consumer is denied before explicit Harnex authorization;
4. authorization applies to the exact observed package + signer identity;
5. authorized connect/disconnect/reconnect passes;
6. an unknown/mismatched identity cannot inherit access;
7. no signing key/password/full certificate or inference content is persisted.

For a physical Play distribution claim, the installed Internal builds' actual Play App Signing digest identities are recorded and the user-visible authorization/connect flow succeeds on device.

For a real-runtime/device claim, additionally require the exact curated model/runtime identity plus the applicable generation, cancellation, memory, JNI and thermal evidence. The public API/security/versioning/release-notes review must cover the same candidate.

The evidence tooling and deterministic independent-signer E2E may merge before physical confirmation. Stable promotion remains blocked only on the REAL_ENVIRONMENT evidence that the exact promotion claim genuinely requires.