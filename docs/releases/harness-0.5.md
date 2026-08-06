# Harness 0.5.0 release checklist

Status: active
Document type: release-checklist
Owner: repository
Last reviewed: 2026-08-06

This file owns only the remaining release gates for Harness 0.5.0. Historical integration sequencing is retained through Git history and the archived integration summary; current implementation status belongs in [`../current-state.md`](../current-state.md).

## Integrated scope

Harness 0.5.0 currently includes:

- protected `dev` integration and validated `dev -> main` promotion workflows;
- the embedded Android GGUF runtime over the pinned `llama.cpp` backend;
- content-addressed model storage, integrity verification and curated remote distribution boundaries;
- the connected Compose phone application with Overview, Playground, Models, Diagnostics and Settings;
- real model import, download/install, selection, verification, streaming generation, cancellation and protected removal;
- shared runtime ownership for Playground and physical validation;
- ViewModel/UDF ownership for Playground and Models;
- typed Settings, request-timeline and model-detail navigation;
- unified catalog/import/selection/runtime model inventory and deterministic recovery;
- model-aware generation presets, prompt planning, output constraints, stop handling and repetition protection;
- telemetry, health, resources, logs, request timelines and benchmark history;
- reproducible launcher identity, shared design system and Android packaging checks.

## Open product gates

- [ ] Complete the remaining Overview, Diagnostics and Settings state/effect migration out of `MainActivity`.
- [ ] Complete deterministic navigation restoration and process-recreation behavior.
- [ ] Add the explicit non-destructive `Load in memory` and `Unload from memory` product actions.
- [ ] Implement configurable monotonic warm-idle TTL eviction with race, pinning and unload-reason coverage.
- [ ] Complete compact, expanded, landscape and large-font Compose validation.
- [ ] Add screenshot regression and TalkBack/accessibility evidence for the connected states.
- [ ] Validate catalog download, installation, model recovery and generation on representative physical `arm64-v8a` devices.

## Release and physical-device gates

- [ ] Confirm the repository-level ruleset for `dev` rejects direct push, force-push and deletion and requires the repository validation check.
- [ ] Build a signed release AAB with the external upload key.
- [ ] Upload the exact candidate to Google Play Internal Testing.
- [ ] Install the Play-delivered build on representative hardware.
- [ ] Inspect, import, verify and load a supported external GGUF.
- [ ] Generate and stream through the real JNI backend.
- [ ] Cancel during prefill and decode.
- [ ] Repeat load, generate, release and unload cycles without unbounded memory growth.
- [ ] Record cold/warm latency, throughput, PSS and thermal evidence.
- [ ] Capture privacy-safe evidence without prompt, output, private path, document URI, signed URL or complete digest disclosure.
- [ ] Promote the final green `dev` candidate to `main` using a merge commit.
- [ ] Tag Harness 0.5.0 only from the validated `main` commit.

## Required procedures

- Build and emulator workflow: [`../android-build-and-run.md`](../android-build-and-run.md)
- Upload-key configuration: [`../android-upload-key.md`](../android-upload-key.md)
- ADB/device lifecycle: [`../device-e2e-testing.md`](../device-e2e-testing.md)
- Evidence format: [`../device-e2e-evidence.md`](../device-e2e-evidence.md)
- Play Internal Testing: [`../play-internal-phone-test.md`](../play-internal-phone-test.md)
- Completion criteria: [`../definition-of-done.md`](../definition-of-done.md)

## Release decision

Harness 0.5.0 must not be described as production-ready until the physical-device lifecycle, JNI loading, cancellation, memory and performance evidence above is complete. Host tests, Android assembly and emulator execution remain merge evidence, not physical-device release evidence.
