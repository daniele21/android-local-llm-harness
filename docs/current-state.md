# Current repository state

Status: active
Document type: current-state
Owner: repository
Canonical scope: state.repository
Read when: determining the integrated baseline, open blockers or next repository work block
Last reviewed: 2026-08-14

This is the single operational ledger for what is integrated, what remains blocked and which implementation block is next. Capability history belongs in [`roadmap.md`](roadmap.md); release gates belong in [`releases/harness-0.5.md`](releases/harness-0.5.md).

## Integration lines

- `dev` is the canonical base and target for ordinary feature, fix, dependency, UX/UI and documentation work.
- `main` is the protected stable and release-oriented line.
- New work must start from the latest green `dev` unless it is an explicit emergency hotfix based on `main`.

## Integrated functional boundary

### Runtime and backend

- pinned `llama.cpp` source and reproducible Android `arm64-v8a` packaging;
- metadata-only GGUF inspection and content-addressed SHA-256 model storage;
- load, context creation, generation, streaming, cancellation and single-decode scheduling;
- model-aware prompt/context planning, output constraints and stop handling;
- versioned presets and request overrides;
- Qwen3.5 typed thinking through Jinja `enable_thinking`, with no `/think` or `/nothink` prompt injection;
- Qwen3.5 end-to-end `minP`, `presencePenalty`, temperature, top-p, top-k, repetition, seed and output-token configuration;
- tier-aware Fast, Quality, Thinking, Precise and JSON Qwen3.5 generation profiles;
- bounded Qwen3.5 generation guard with thinking budget, repetition/runaway detection and typed terminal stop reasons;
- approved Qwen3.5 1K/2K/4K/8K mobile context tiers with a safety reserve and smallest-fitting Auto selection;
- Qwen3.5 recurrent-state reuse capabilities fail closed against the exact pinned backend revision;
- native packaging includes and verifies the `libllama-common.so` renderer dependency;
- typed configuration/runtime/cancellation failures without backend-message disclosure.

### Model distribution and inventory

- executable curated catalog restricted to seven Qwen3.5 dense 0.8B/2B releases;
- no consumer-facing arbitrary GGUF document import or generic family extension point;
- verified HTTPS transfer with size/SHA validation, GGUF inspection and explicit installation;
- catalog-anchored product binding and durable path-free installed metadata;
- external-import inventory origin/projection removed;
- out-of-catalog selections or persisted metadata are not synthesized as legacy/unsupported product state;
- Models reconciles catalog availability, installation, selection and runtime state in one list;
- `Unload from memory` is distinct from destructive `Remove installed model` and preserves installed/selected identity;
- developer/device-test artifact injection remains isolated from consumer APIs.

### Qwen3.5 compatibility and runtime baseline

Q35-1 through Q35-5 are complete; Q35-6 is active:

- closed catalog-only product surface is validated;
- exact Qwen3.5 0.8B Q4_K_M and 2B Q4_K_M artifacts are pinned by SHA-256, size and trusted GGUF structural fingerprints;
- pinned llama.cpp revision is `aedb2a5e9ca3d4064148bbb919e0ddc0c1b70ab3`;
- final compatibility smoke passes `load -> tokenize -> minimal generate` for both reference artifacts;
- effective thinking/sampler configuration is privacy-safe telemetry;
- anomalous thinking/repetition is bounded with typed stop semantics;
- recurrent-state snapshot/session/prefix reuse remains disabled until explicitly proved safe;
- 0.8B and 2B candidate runtime profiles are separate and remain `CANDIDATE` pending physical evidence.

Catalog availability still does not imply certification.

### Connected Android application

`apps/local-llm-phone-test` provides:

- Overview, Playground, Models, Diagnostics and Settings;
- curated Qwen3.5 download/install/select/verify/remove plus inference, streaming and cancellation;
- unified Models lifecycle presentation and explicit runtime unload;
- default Playground prompt `how much is the earth radius?`;
- Qwen3.5 profile selection plus thinking, temperature, top-p, top-k, min-p, presence penalty, repeat penalty/window, seed and context controls;
- ViewModel/UDF ownership for Playground and Models;
- real run, health, resource, benchmark, log and request-timeline data;
- reproducible launcher identity, dark/light/system foundations and baseline accessibility checks.

Overview, Diagnostics and Settings still retain some Activity-owned renderable state/effects; `MainActivity` is not yet a pure composition/lifecycle root.

### Observability

- bounded in-memory and Room-backed telemetry;
- request-correlated logs and privacy-safe timelines;
- queue, model-load, TTFT, prefill, decode, total, token and throughput metrics;
- effective generation metadata including thinking and scalar sampling configuration;
- model-integrity, generation-sanity and cache-health checks;
- Android memory and thermal snapshots;
- benchmark baseline/history separation and matching-sample regression evaluation;
- strict benchmark execution identity prevents comparisons across incompatible context/preset/thinking/sampler/seed/template configurations;
- Room schema v8 persists execution identity and drops unverifiable legacy baselines rather than inventing compatibility identity.

Normal telemetry excludes prompt/output/system-prompt/template/schema/stop-sequence text, filesystem paths, document URIs and signed URLs.

### Shared Android runtime

The shared-runtime implementation has advanced beyond proposal status:

- SR-0 decision/scope, SR-1 Binder protocol v1, SR-2 authenticated host service and SR-3 lifecycle-safe Binder client SDK are integrated;
- the host uses a signature-protected exported service, exact component binding, host-owned application/use-case/model resolution and per-client ownership ledgers;
- the client exposes `BinderLocalLlmClient` with typed connection states, bounded ordered callbacks, deterministic cancellation, epoch invalidation and disconnect handling;
- SR-4 two-APK debug instrumentation and `scripts/run-shared-runtime-device-e2e.sh` are integrated, while the actual emulator/device execution evidence remains pending;
- SR-5 deterministic isolation, duplicate external-ID separation, client cleanup, callback backpressure, disconnect convergence and Binder-boundary privacy hardening are integrated and green;
- SR-6 release-like evidence tooling uses a packaged release client AAR consumer, same-signer host/client tests, process-death/reconnect validation, an independently signed denial fixture and privacy-safe evidence capture.

Shared-runtime production/release readiness is still blocked until SR-6 is executed on representative physical hardware and the exact candidate passes release review. See [`shared-runtime/roadmap.md`](shared-runtime/roadmap.md) and [`shared-runtime/sr6-release-evidence.md`](shared-runtime/sr6-release-evidence.md).

### Public Consumer API

The application-facing API is now an active implementation track rather than a documentation-only proposal:

- CA-0 boundary decisions are accepted in ADR 0013;
- CA-1 capability/policy discovery is integrated and keeps exact model/artifact selection host-owned;
- CA-2 `ConsumerLocalLlmClient` is integrated as the constrained public facade for discover -> prepare -> session -> generate -> close;
- CA-3 public result, surfaced-reasoning, execution-identity and Tier 1/Tier 2 metric projection is integrated in `dev`;
- CA-4 Binder integration is active on PR #104 and evolves Binder v1 append-only to protocol minor 1 with the optional `consumer-api-v1` feature;
- the active CA-4 branch contains consumer-specific AIDL/wire DTOs, host mapping, Binder consumer lifecycle/generation adapters, deterministic host/client/wire/compatibility tests and a packaged-AAR Consumer API compilation fixture;
- CA-4 remains `IN PROGRESS` until repository and documentation validation are green on the exact branch head and the PR is integrated in `dev`;
- CA-5 OMBRA reference-consumer migration must not start from an unvalidated CA-4 public transport boundary.

Canonical sequence and exit gates are owned by [`shared-runtime/consumer-api/roadmap.md`](shared-runtime/consumer-api/roadmap.md).

## Open implementation blocks

### 1. CA-4 Binder Consumer API exit gate

The implementation and deterministic evidence are now present on the active branch. Remaining merge gate:

- obtain green repository and documentation validation on the exact PR head;
- fix any failures without weakening the security/privacy/compatibility contract;
- only after the exact head is green, mark CA-4 `DONE` and merge PR #104 into `dev`.

Specification: [`shared-runtime/consumer-api/ca4-binder-protocol.md`](shared-runtime/consumer-api/ca4-binder-protocol.md).

### 2. Q35-6 physical Android tuning evidence

Repository-side tuning infrastructure is complete. Remaining work:

- run the controlled matrix for both curated Qwen3.5 0.8B Q4_K_M and 2B Q4_K_M artifacts on representative physical Android hardware;
- collect one cold plus at least three warm samples per case with exact device/runtime identity;
- review TTFT, prefill/decode throughput, peak PSS, available memory and thermal evidence independently by model tier;
- choose versioned measured defaults only from complete comparable evidence;
- validate cancellation, model switching, memory pressure and idle unload on the selected configurations.

Specification: [`qwen35/workstreams/runtime-tuning.md`](qwen35/workstreams/runtime-tuning.md).

### 3. SR-6 shared-runtime physical release evidence

Repository-side release-evidence tooling is implemented. Remaining work:

- execute the release-like same-signer host and packaged-client flow on a physical `arm64-v8a` device;
- verify prepare/session/stream/complete/cancel/close against the host-selected curated Qwen3.5 model;
- capture process-death/reconnect, memory, thermal and privacy-safe timing/token evidence;
- execute the independently signed negative fixture and require `PERMISSION_DENIED`;
- compare Binder overhead against a matching in-process run on the same device/model/profile identity;
- complete package replacement/upgrade coverage plus public API, security, versioning and release-note review.

Runbook: [`shared-runtime/sr6-release-evidence.md`](shared-runtime/sr6-release-evidence.md).

### 4. Q35-7 validation suite

After Q35-6 measured profiles exist:

- execute semantic/golden validation across supported thinking/output modes;
- validate context boundaries and cancellation during prefill/decode;
- run repeated lifecycle, memory and thermal device evidence for both tiers;
- prepare certification-consumable evidence without conflating catalog availability with support certification.

### 5. Remaining phone-app UDF migration

- move Overview, Diagnostics and Settings renderable state/intent behind typed state/effect boundaries;
- reduce Activity-owned mirrors;
- complete deterministic restoration and Back behavior without persisting sensitive content.

### 6. RAM residency policy completion

Explicit manual unload is implemented. Remaining residency work is:

- configurable warm-idle TTL using an injected monotonic clock and cancellable scheduler;
- ownership rechecks at eviction time;
- privacy-safe automatic unload reasons for TTL, memory pressure, switch and shutdown;
- race, pinning, idempotent cleanup and reload-classification validation.

### 7. UI/accessibility and release evidence

- compact/expanded/landscape/large-font and screenshot-regression coverage;
- TalkBack/focus-order and recreation evidence;
- signed Internal Testing candidate.

## Immediate next block

Complete the CA-4 validation/integration gate before widening the public consumer surface:

1. require green repository and documentation validation for the exact CA-4 head;
2. fix any deterministic validation failures with dedicated commits;
3. mark CA-4 `DONE` only after the exit gate is green and merge PR #104 into `dev`;
4. start CA-5 OMBRA reference-consumer work from the resulting green `dev` baseline.

The two device-dependent tracks remain parallel and may share the same representative hardware session:

- complete **Q35-6 physical-device evidence** using `scripts/run-qwen35-tuning-matrix.sh` for the 0.8B and 2B curated reference artifacts;
- execute **SR-6 release-like shared-runtime evidence** using `scripts/capture-shared-runtime-release-evidence.sh` against the exact host-selected model/profile identity.

Do not promote Q35 candidate profiles to `MEASURED`, publish the Binder client AAR or describe the shared host/public consumer transport as production-ready from emulator/CI results alone.

## Blockers and deferred evidence

- CA-4 cannot be marked `DONE` until repository/documentation validation passes on the exact branch head and the PR is integrated;
- CA-5 should start only from the integrated CA-4 public transport boundary;
- representative physical-device performance evidence is required to close Q35-6;
- Q35-7/Q35-8 require the measured Q35-6 profile evidence;
- SR-4 execution evidence and SR-6 physical release evidence remain pending until run on the exact recorded host/client/runtime/model identity;
- SR-6 consumer release also requires matching Binder-overhead evidence, invalid-signer denial, compatibility/replacement review and final public API/security/versioning review;
- Capacitor remains a later integration phase after the Android shared-runtime/public-consumer release boundary is validated;
- GPU/Vulkan, simultaneous decode, multimodal and speculative decoding remain outside the current production-capable scope.

## Source links

- Capability milestones: [`roadmap.md`](roadmap.md)
- Shared runtime roadmap: [`shared-runtime/roadmap.md`](shared-runtime/roadmap.md)
- Public Consumer API roadmap: [`shared-runtime/consumer-api/roadmap.md`](shared-runtime/consumer-api/roadmap.md)
- CA-4 Binder specification: [`shared-runtime/consumer-api/ca4-binder-protocol.md`](shared-runtime/consumer-api/ca4-binder-protocol.md)
- SR-6 release evidence: [`shared-runtime/sr6-release-evidence.md`](shared-runtime/sr6-release-evidence.md)
- Qwen3.5-only product status: [`qwen35/README.md`](qwen35/README.md)
- Target behavior: [`implementation-plan.md`](implementation-plan.md)
- Phone architecture: [`features/phone-app-architecture.md`](features/phone-app-architecture.md)
- Generation behavior: [`generation-configuration-and-prompting-plan.md`](generation-configuration-and-prompting-plan.md)
- Harness 0.5 release gates: [`releases/harness-0.5.md`](releases/harness-0.5.md)
