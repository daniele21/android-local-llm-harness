# Current repository state

Status: active
Document type: current-state
Owner: repository
Canonical scope: state.repository
Read when: determining the integrated baseline, open blockers or next repository work block
Last reviewed: 2026-08-14

This is the single operational ledger for the integrated baseline, blockers and immediate next work. Capability history belongs in [`roadmap.md`](roadmap.md); focused milestone detail belongs in its workstream roadmap/specification; release gates belong in [`releases/harness-0.5.md`](releases/harness-0.5.md).

## Integration lines

- `dev` is the canonical base and target for ordinary feature, fix, UX/UI and documentation work.
- `main` is the protected stable/release line.
- New work starts from the latest green `dev` unless it is an explicit emergency hotfix.

## Integrated baseline

### Embedded runtime and models

- pinned `llama.cpp`, reproducible Android `arm64-v8a` packaging and opaque native ownership;
- GGUF inspection, SHA-256 content-addressed storage, verified curated installation and explicit model lifecycle;
- load, context creation, generation, streaming, cancellation, single-decode scheduling and memory-pressure handling;
- model-aware prompt/context planning, output constraints, versioned presets and privacy-safe failures;
- product catalog restricted to curated Qwen3.5 dense 0.8B/2B artifacts; exact artifact choice remains Harness-owned.

Q35-1 through Q35-5 are complete. Q35-6 remains active because the 0.8B/2B candidate profiles still require representative physical-device tuning evidence. See [`qwen35/README.md`](qwen35/README.md).

### Android application and observability

`apps/local-llm-phone-test` has connected Overview, Playground, Models, Diagnostics and Settings surfaces with real model management, inference, streaming/cancellation, telemetry, health, resources, benchmarks and request timelines.

Remaining app hardening is primarily:

- UDF migration for Overview, Diagnostics and Settings;
- recreation/back-stack and state-restoration evidence;
- accessibility, large-font, landscape/expanded and screenshot coverage;
- final signed Internal Testing evidence.

### Shared Android runtime

SR-0 through SR-5 are integrated. SR-6 repository-side release-evidence tooling is integrated, including packaged-client, same-signer/invalid-signer and process-death/reconnect fixtures.

The shared runtime is not production/release ready until the physical SR-6 evidence is executed on representative hardware. Canonical status and runbook: [`shared-runtime/roadmap.md`](shared-runtime/roadmap.md) and [`shared-runtime/sr6-release-evidence.md`](shared-runtime/sr6-release-evidence.md).

### Public Consumer API

- CA-0 boundary decisions, CA-1 capability policy, CA-2 `ConsumerLocalLlmClient` and CA-3 results/metrics are integrated in `dev`;
- CA-3 is `DONE` after PR #103;
- CA-4 Binder integration is active on PR #104 and remains `IN PROGRESS` until its exact head is green and integrated;
- the CA-4 branch contains Binder v1.1 `consumer-api-v1`, consumer AIDL/wire contracts, authenticated host mapping, Binder lifecycle/generation adapters, deterministic host/client/wire/compatibility tests and a packaged-AAR Consumer API fixture.

CA-5 OMBRA must start only from the integrated CA-4 boundary. Canonical milestone state: [`shared-runtime/consumer-api/roadmap.md`](shared-runtime/consumer-api/roadmap.md). CA-4 behavior and exit evidence: [`shared-runtime/consumer-api/ca4-binder-protocol.md`](shared-runtime/consumer-api/ca4-binder-protocol.md).

## Open blockers

### 1. CA-4 validation and integration

The deterministic implementation/evidence is present on PR #104. Remaining gate:

1. repository and documentation validation green on the exact head;
2. fix failures without weakening security, privacy or compatibility semantics;
3. mark CA-4 `DONE` only after the gate passes;
4. merge PR #104 into `dev`.

### 2. Physical Android evidence

Two device-dependent tracks remain open and can share a representative hardware session:

- **Q35-6** — run the controlled Qwen3.5 0.8B/2B tuning matrix, collect cold/warm timing, throughput, PSS, available-memory and thermal evidence, then select measured defaults;
- **SR-6** — run release-like same-signer Binder evidence, invalid-signer denial, process-death/reconnect and matching Binder-vs-in-process overhead evidence.

Do not promote Q35 profiles to `MEASURED`, publish the Binder client AAR or describe the shared host/public transport as production-ready from CI/emulator evidence alone.

### 3. Follow-on validation and product hardening

After Q35-6, Q35-7 must run semantic/golden, context-boundary, cancellation, lifecycle, memory and thermal validation. Remaining phone work includes UDF completion, RAM warm-idle TTL policy, accessibility/responsive coverage and signed release evidence.

## Immediate next block

1. close the failing CA-4 repository/documentation checks on PR #104;
2. when the exact head is green, update CA-4 to `DONE` and merge #104 into `dev`;
3. start CA-5 OMBRA from that green integrated baseline;
4. keep Q35-6 and SR-6 physical evidence as the parallel release-readiness track.

## Source links

- Capability roadmap: [`roadmap.md`](roadmap.md)
- Consumer API roadmap: [`shared-runtime/consumer-api/roadmap.md`](shared-runtime/consumer-api/roadmap.md)
- CA-4 Binder specification: [`shared-runtime/consumer-api/ca4-binder-protocol.md`](shared-runtime/consumer-api/ca4-binder-protocol.md)
- Shared runtime roadmap: [`shared-runtime/roadmap.md`](shared-runtime/roadmap.md)
- SR-6 evidence runbook: [`shared-runtime/sr6-release-evidence.md`](shared-runtime/sr6-release-evidence.md)
- Qwen3.5 status: [`qwen35/README.md`](qwen35/README.md)
- Harness 0.5 release gates: [`releases/harness-0.5.md`](releases/harness-0.5.md)
