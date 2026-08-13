# Harness 0.5.0 release checklist

Status: active
Document type: release-checklist
Owner: repository
Canonical scope: release.harness-0.5
Read when: preparing, validating or promoting the Harness 0.5.0 release candidate
Last reviewed: 2026-08-13

This file owns only the remaining release gates for Harness 0.5.0. Historical integration sequencing is retained through Git history and the archived integration summary; current implementation status belongs in [`../current-state.md`](../current-state.md).

## Integrated scope

Harness 0.5.0 currently includes:

- protected `dev` integration and validated `dev -> main` promotion workflows;
- the embedded Android GGUF runtime over the pinned `llama.cpp` backend;
- content-addressed model storage, integrity verification and curated Qwen3.5 remote distribution boundaries;
- the connected Compose phone application with Overview, Playground, Models, Diagnostics and Settings;
- curated model download/install, selection, verification, streaming generation, cancellation, explicit runtime unload and protected removal;
- shared runtime ownership for Playground and physical validation;
- ViewModel/UDF ownership for Playground and Models;
- typed Settings, request-timeline and model-detail navigation;
- unified catalog/installation/selection/runtime model inventory and deterministic recovery;
- model-aware generation presets, prompt planning, output constraints, stop handling and repetition protection;
- telemetry, health, resources, logs, request timelines and benchmark history;
- reproducible launcher identity, shared design system and Android packaging checks;
- shared-runtime Binder protocol v1, authenticated host service and lifecycle-safe Android client SDK;
- two-APK debug Binder instrumentation and deterministic multi-client/death/backpressure/privacy hardening;
- SR-6 release-like physical evidence tooling, packaged release client-AAR consumer and independent-signer denial fixture.

## Open product gates

- [ ] Complete the remaining Overview, Diagnostics and Settings state/effect migration out of `MainActivity`.
- [ ] Complete deterministic navigation restoration and process-recreation behavior.
- [ ] Implement configurable monotonic warm-idle TTL eviction with race, pinning and unload-reason coverage.
- [ ] Complete compact, expanded, landscape and large-font Compose validation.
- [ ] Add screenshot regression and TalkBack/accessibility evidence for the connected states.
- [ ] Validate curated Qwen3.5 download, installation, recovery and generation on representative physical `arm64-v8a` devices.

## Qwen3.5 physical runtime gates

- [ ] Complete Q35-6 controlled physical tuning evidence for both curated reference tiers where claimed.
- [ ] Record exact model digest, runtime/profile identity and pinned `llama.cpp` revision for each measured candidate.
- [ ] Record cold/warm TTFT, prefill/decode throughput, PSS and thermal evidence.
- [ ] Validate cancellation, model switching, memory pressure and idle unload on the selected measured configurations.
- [ ] Complete the downstream Q35 validation/certification gates before describing a catalog model/profile as supported rather than merely available.

## Shared-runtime SR-6 release gates

- [ ] Execute `scripts/capture-shared-runtime-release-evidence.sh` on representative physical `arm64-v8a` hardware using the exact release candidate.
- [ ] Require same-signer host, packaged client consumer and instrumentation APK certificate identities to match.
- [ ] Prepare the exact host-selected curated Qwen3.5 model and record its digest without exposing host-private paths.
- [ ] Complete prepare/session/stream/complete/cancel/close over the real Binder/process boundary.
- [ ] Record cancellation latency and host process-death -> typed disconnect -> restart/reconnect behavior.
- [ ] Execute the ephemeral independently signed consumer fixture and require `PERMISSION_DENIED` before runtime negotiation.
- [ ] Capture reviewable memory and thermal snapshots without prompt/output, Binder token, signing secret, GGUF byte or private-path disclosure.
- [ ] Compare Binder overhead against a matching in-process run on the same device/model/profile identity.
- [ ] Complete host/client package replacement and supported protocol compatibility evidence.
- [ ] Complete public API, security, versioning, packaging and consumer-sample review.
- [ ] Bind the release record to harness commit, host version, client SDK version, Binder protocol major/minor, negotiated features, model digest and pinned `llama.cpp` revision.

SR-6 remains **IN PROGRESS** until all applicable physical and review gates above are satisfied. Repository CI and emulator execution alone cannot close it.

## Release and physical-device gates

- [ ] Confirm the repository-level ruleset for `dev` rejects direct push, force-push and deletion and requires the repository validation check.
- [ ] Build a signed release AAB with the external upload key.
- [ ] Upload the exact candidate to Google Play Internal Testing.
- [ ] Install the Play-delivered build on representative hardware.
- [ ] Generate and stream through the real JNI backend using a curated Qwen3.5 artifact.
- [ ] Cancel during prefill and decode.
- [ ] Repeat load, generate, release and unload cycles without unbounded memory growth.
- [ ] Record cold/warm latency, throughput, PSS and thermal evidence.
- [ ] Capture privacy-safe evidence without prompt, output, private path, document URI, signed URL or signing-secret disclosure.
- [ ] Promote the final green `dev` candidate to `main` using a merge commit.
- [ ] Tag Harness 0.5.0 only from the validated `main` commit.

## Required release identity

The candidate/release record must bind, when applicable:

```text
harness git commit
host package + version/build
shared-runtime client SDK version
Binder protocol major/minor
negotiated protocol minor/features
host/client signing-certificate digest identity
selected curated Qwen3.5 model digest
pinned llama.cpp revision
physical Android device/version/ABI
```

Certificate digests are recorded only as identity evidence. Full certificates, private keys and passwords are not release artifacts.

## Required procedures

- Build and emulator workflow: [`../android-build-and-run.md`](../android-build-and-run.md)
- Upload-key configuration: [`../android-upload-key.md`](../android-upload-key.md)
- ADB/device lifecycle: [`../device-e2e-testing.md`](../device-e2e-testing.md)
- Evidence format: [`../device-e2e-evidence.md`](../device-e2e-evidence.md)
- Shared-runtime SR-6 evidence: [`../shared-runtime/sr6-release-evidence.md`](../shared-runtime/sr6-release-evidence.md)
- Play Internal Testing: [`../play-internal-phone-test.md`](../play-internal-phone-test.md)
- Completion criteria: [`../definition-of-done.md`](../definition-of-done.md)
- Versioning and release identity: [`../versioning.md`](../versioning.md)

## Release decision

Harness 0.5.0 must not be described as production-ready until the physical-device Qwen3.5 lifecycle, JNI loading, cancellation, memory/performance/thermal evidence and applicable shared-runtime SR-6 release evidence are complete. Host tests, Android assembly, repository CI and emulator execution remain merge evidence, not physical-device release evidence.
