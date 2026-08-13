# SR-6 shared-runtime release evidence

Status: active
Document type: evidence-runbook
Owner: shared-runtime-validation
Canonical scope: shared-runtime.sr6-release-evidence
Read when: preparing or reviewing the physical two-APK release gate
Last reviewed: 2026-08-13

## Goal

SR-6 validates the exact deployment boundary that an application consumer would receive: a separately installed host APK, a packaged Binder client AAR consumed by a separate APK, signature-protected Binder authorization and release-like signing identities.

This gate is deliberately stronger than SR-4 emulator/debug preflight. Emulator execution may prove installation and wiring only; it cannot close SR-6.

## Runner

Use:

```bash
bash scripts/capture-shared-runtime-release-evidence.sh --device <adb-serial>
```

The runner requires a physical `arm64-v8a` device by default. `--allow-emulator` is available only for explicitly labelled preflight evidence.

The positive host and consumer fixture use the existing external phone-test signing configuration:

```text
LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_STORE_FILE
LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_STORE_PASSWORD
LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_KEY_ALIAS
LOCAL_LLM_PHONE_TEST_ANDROID_UPLOAD_KEY_PASSWORD
```

On macOS the runner may reuse the same Keychain entry documented by `scripts/build-phone-test-release.sh`. Signing secrets are never written to the evidence directory.

## Model setup

The runner never downloads, copies or discovers a host-private GGUF path. The release host must already have a curated Qwen3.5 model installed and selected.

For first-time setup:

```bash
bash scripts/capture-shared-runtime-release-evidence.sh \
  --device <adb-serial> \
  --host-only
```

This builds, installs and launches the signed release host while preserving its app-private state. Install/select the curated model through the host UI, then rerun the normal evidence command.

## Positive same-signer flow

The runner builds:

- `apps/local-llm-phone-test` release APK;
- `transports/android-binder-client` release AAR;
- `transports/android-binder-contract` release AAR;
- `apps/shared-runtime-client-consumer-fixture` release APK and instrumentation APK.

The consumer fixture references the packaged AARs rather than project-source client classes. Before installation, the runner verifies that host, client and instrumentation APKs have the same SHA-256 signing-certificate digest.

`SharedRuntimeReleaseEvidenceTest` then verifies over the real Binder/process boundary:

- bind and registration;
- prepare against the host-selected curated model;
- session creation;
- streamed generation and successful completion;
- privacy-safe TTFT/total/token/throughput markers;
- active generation cancellation and cancellation latency;
- session close;
- host process termination, typed `CONNECTION_LOST`, host restart and clean reconnect.

The instrumentation test does not print prompt or generated output content.

## Independent-signer denial

Unless `--skip-negative` is used, the runner creates a short-lived PKCS12 key in a temporary directory, rebuilds only the consumer fixture with that independent identity, and verifies that its certificate digest differs from the still-installed host.

`SharedRuntimeInvalidSignerTest` must reach `PERMISSION_DENIED` before runtime negotiation. The temporary keystore and password are deleted when the runner exits and are never committed or copied into the evidence bundle.

Skipping this case leaves SR-6 incomplete.

## Evidence bundle

Default output:

```text
build/shared-runtime-evidence/<UTC timestamp>/
build/shared-runtime-evidence/<UTC timestamp>.tar.gz
```

The bundle contains:

- `manifest.txt` — exact git commit, physical/emulator scope, package/version identities, signing-certificate digests, client SDK version, protocol version and pinned llama.cpp revision;
- `device.txt` — manufacturer/model/codename, Android release/SDK and ABI, excluding adb serial;
- `positive-instrumentation.log` — functional/cancellation/process-death test result;
- `negative-instrumentation.log` — independent-signer denial result when enabled;
- `metrics.txt` — privacy-safe `SR6_SHARED_RUNTIME` markers;
- `filtered-logcat.txt` — only SR-6 markers and relevant Android runtime failures;
- host `meminfo` and thermal snapshots before/after the run;
- APK hashes;
- a privacy/readme summary.

The bundle excludes:

- prompts and generated output;
- GGUF bytes and host-private model paths;
- Binder client tokens;
- adb serial numbers;
- keystores, signing passwords and full certificates.

## Acceptance

A physical SR-6 record is reviewable only when:

1. `scope=PHYSICAL_RELEASE_EVIDENCE`;
2. `result_positive=PASS`;
3. `result_invalid_signer=PASS`;
4. host/client/test positive certificate digests match;
5. the negative client digest differs from the host digest;
6. the repository checkout is clean and its exact commit is recorded;
7. generation, cancellation and process-death markers are present;
8. no prompt/output or signing material is persisted;
9. applicable Q35 physical model/runtime evidence exists for the model/profile being claimed;
10. the public API, security, versioning and release notes review is completed for the same candidate.

The evidence tooling can be merged before a physical run, but SR-6 remains **IN PROGRESS** until the physical record and release review satisfy all exit criteria.
