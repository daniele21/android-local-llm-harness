# Current repository state

Status: active
Document type: current-state
Owner: repository
Canonical scope: state.repository
Read when: determining the integrated baseline, open blockers or next repository work block
Last reviewed: 2026-08-08

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

### Qwen3.5 compatibility baseline

Q35-1, Q35-2 and Q35-3 are complete:

- closed catalog-only product surface is validated;
- exact Qwen3.5 0.8B Q4_K_M and 2B Q4_K_M artifacts are pinned by SHA-256, size and trusted GGUF structural fingerprints;
- pinned llama.cpp revision is `aedb2a5e9ca3d4064148bbb919e0ddc0c1b70ab3`;
- final compatibility smoke passes `load -> tokenize -> minimal generate` for both reference artifacts;
- effective thinking/sampler configuration is privacy-safe telemetry;
- repository Validate and Android Package gates are green on the completed implementation.

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
- benchmark baseline/history separation and matching-sample regression evaluation.

Normal telemetry excludes prompt/output/system-prompt/template/schema/stop-sequence text, filesystem paths, document URIs and signed URLs.

## Open implementation blocks

### 1. Q35-4 generation guard

Next Qwen3.5 milestone:

- define versioned bounded anomaly/repetition thresholds and optional thinking budget;
- implement bounded token-window guard execution in `core/runtime-core`;
- emit typed guard stop reasons distinct from cancellation, max tokens and backend failure;
- preserve correct streaming cleanup and privacy-safe telemetry;
- add deterministic guard tests without duplicating JSON/schema validation.

Owner: [`qwen35/workstreams/generation-thinking.md`](qwen35/workstreams/generation-thinking.md).

### 2. Q35-5 runtime/context/cache capability model

- define approved mobile context tiers;
- verify Qwen3.5 hybrid/recurrent state behavior before enabling snapshot/restore/prefix reuse;
- capability-gate unsupported reuse optimizations;
- preserve memory-pressure and cancellation cleanup.

### 3. Remaining phone-app UDF migration

- move Overview, Diagnostics and Settings renderable state/intent behind typed state/effect boundaries;
- reduce Activity-owned mirrors;
- complete deterministic restoration and Back behavior without persisting sensitive content.

### 4. RAM residency policy completion

Explicit manual unload is implemented. Remaining residency work is:

- configurable warm-idle TTL using an injected monotonic clock and cancellable scheduler;
- ownership rechecks at eviction time;
- privacy-safe automatic unload reasons for TTL, memory pressure, switch and shutdown;
- race, pinning, idempotent cleanup and reload-classification validation.

### 5. UI/accessibility and physical-device evidence

- compact/expanded/landscape/large-font and screenshot-regression coverage;
- TalkBack/focus-order and recreation evidence;
- signed Internal Testing candidate;
- representative `arm64-v8a` load/generate/memory/thermal evidence;
- cancellation during prefill/decode and repeated lifecycle stability.

## Immediate next block

Proceed with **Q35-4 generation guard**. Do not reopen generic model-selection or retired-family compatibility paths. Q35-1 through Q35-3 are closed; Q35-5 tuning/cache work starts only after the guard boundary is explicit and tested.

## Blockers and deferred evidence

- generation-loop/runaway protection remains Q35-4;
- hybrid/recurrent snapshot/prefix reuse remains unproven and capability-gated pending Q35-5;
- representative physical-device performance evidence is still required before production-readiness or certification claims;
- Binder/AIDL shared runtime and Capacitor remain later phases;
- GPU/Vulkan, simultaneous decode, multimodal and speculative decoding remain outside the current production-capable embedded scope.

## Source links

- Capability milestones: [`roadmap.md`](roadmap.md)
- Qwen3.5-only product status: [`qwen35/README.md`](qwen35/README.md)
- Target behavior: [`implementation-plan.md`](implementation-plan.md)
- Phone architecture: [`features/phone-app-architecture.md`](features/phone-app-architecture.md)
- Generation behavior: [`generation-configuration-and-prompting-plan.md`](generation-configuration-and-prompting-plan.md)
- Harness 0.5 release gates: [`releases/harness-0.5.md`](releases/harness-0.5.md)
