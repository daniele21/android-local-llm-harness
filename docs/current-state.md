# Current repository state

Status: active
Document type: current-state
Owner: repository
Last reviewed: 2026-08-06

This is the single operational ledger for what is integrated, what remains blocked and which implementation block is next. Capability history belongs in [`roadmap.md`](roadmap.md); release gates belong in [`releases/harness-0.5.md`](releases/harness-0.5.md).

## Integration lines

- `dev` is the canonical base and target for ordinary feature, fix, dependency, UX/UI and documentation work.
- `main` is the protected stable and release-oriented line.
- PR #75 promoted the validated `dev` candidate to `main` on 2026-08-06.
- The promoted baseline contains the work through PR #76, including repetition-protection controls.
- New work must start from the latest green `dev` unless it is an explicit emergency hotfix based on `main`.

The repository-level ruleset for `dev` remains an administrative verification item: direct push, force-push and deletion protection plus required repository validation must be confirmed before Harness 0.5 release.

## Integrated functional boundary

### Runtime and backend

- pinned `llama.cpp` source and reproducible Android `arm64-v8a` packaging;
- metadata-only GGUF inspection;
- opaque model and context handles;
- content-addressed import, SHA-256 verification, deduplication and integrity checks;
- load, context creation, generation, aggregated streaming and cooperative cancellation;
- single-decode scheduling with priorities and queue cancellation;
- compatible warm model reuse, protected model switching and Android memory-pressure handling;
- model-aware prompt planning, exact tokenization, lazy Auto/manual context selection, output constraints and stop handling;
- versioned presets, per-request sampling overrides, random/fixed seed policy and repetition protection;
- typed configuration, runtime and cancellation failures without backend-message disclosure.

### Model distribution and inventory

- curated administrator-managed catalog with application/use-case and device compatibility filtering;
- HTTPS-only verified transfer with bounded redirects, size checks, SHA-256 validation and cancellation;
- opaque verified-download handle and explicit installation boundary;
- durable path-free installed catalog/profile metadata;
- explicit import, download, install, select, verify and remove operations;
- protected removal while the runtime owns or uses a model;
- unified immutable projection across catalog releases, installed metadata, external imports, selection and runtime-loaded ownership;
- explicit degraded states for loaded-model absence or loaded-versus-selected mismatch;
- model-detail presentation and deterministic recovery that never deletes a GGUF as part of runtime release.

### Connected Android application

`apps/local-llm-phone-test` is the connected Compose surface with:

- Overview, Playground, Models, Diagnostics and Settings;
- compact bottom navigation and expanded navigation rail;
- one process-scoped runtime graph shared by Playground and physical validation;
- real GGUF import, inference, streaming, cancellation, cleanup and validation;
- ViewModel/UDF ownership for Playground and Models;
- typed Settings, request-timeline and model-detail routes;
- real run, health, resource, benchmark, log and request-timeline data;
- reproducible launcher identity, dark/light/system design-system foundations and baseline accessibility checks.

Overview, Diagnostics and Settings still retain Activity-owned renderable state or effects. `MainActivity` is not yet reduced to a pure composition and lifecycle root.

### Observability

- bounded in-memory and Room-backed telemetry;
- run lifecycle, request-correlated logs and privacy-safe timelines;
- queue, model-load, TTFT, prefill, decode, total, token and throughput metrics;
- effective generation metadata including sampling, context, template source and repetition protection;
- model-integrity, generation-sanity and cache-health checks;
- Android memory and thermal snapshots;
- active benchmark baselines separated from immutable retained capture history;
- cold/warm isolation and regression evaluation based only on matching post-baseline samples.

Normal telemetry excludes prompt, generated output, system-prompt text, template text, schema, stop-sequence text, filesystem paths, document URIs and signed URLs.

## Open implementation blocks

### 1. Remaining phone-app UDF migration

- move Overview, Diagnostics and Settings renderable state and user-intent coordination behind typed state/effect boundaries;
- remove the corresponding Activity-owned mirrors;
- keep Android launchers and native lifecycle resources scoped to the Activity only where ownership requires it;
- complete state restoration and deterministic Back behavior without persisting sensitive content.

### 2. RAM residency controls

- expose explicit `Load in memory` and `Unload from memory` actions separate from installation and selection;
- reject or defer unload while contexts, active generation or queued work own the model;
- implement a configurable warm-idle TTL using an injected monotonic clock and cancellable scheduler;
- cancel or rearm eviction on reuse and recheck ownership at expiry;
- preserve the installed GGUF, selected identity and metadata;
- record privacy-safe unload reasons for manual action, TTL, memory pressure, switch and shutdown;
- validate races, pinning, idempotent cleanup and reload classification.

### 3. UI and accessibility evidence

- complete deterministic Compose state fixtures;
- add compact, expanded, landscape and large-font coverage;
- add screenshot regression coverage for real empty, unavailable, loading, warning, failure and success states;
- complete TalkBack and focus-order validation;
- verify navigation restoration and process recreation.

### 4. Physical-device and release evidence

- build and sign the exact release candidate;
- distribute it through Google Play Internal Testing;
- validate remote download/install, imported GGUF and real JNI inference on representative `arm64-v8a` hardware;
- cancel during prefill and decode;
- record repeated lifecycle memory stability, cold/warm latency, throughput, PSS and thermal evidence;
- preserve privacy-safe evidence without committing model or signing material.

## Immediate next block

Implement the remaining phone-app UDF migration as one focused vertical slice, starting with Diagnostics because it owns the broadest remaining Activity state and effect surface.

The slice should:

1. define immutable Diagnostics state and typed actions/effects;
2. move section selection, refresh, health, resource, benchmark, log and timeline coordination behind the ViewModel boundary;
3. keep repository, executor and Android-resource ownership in the Activity-scoped effect implementation;
4. remove migrated Activity state only after deterministic JVM coverage passes;
5. verify that navigation and refresh remain observational and do not start diagnostics work implicitly;
6. run focused phone-test formatting, JVM tests, Lint and compilation before the repository clean-checkout gate.

## Blockers and deferred evidence

- Representative physical-device GGUF evidence remains mandatory before production readiness or device-performance claims.
- The standalone console remains intentionally disconnected from another application's private runtime and telemetry unless an explicit bridge is introduced later.
- Binder/AIDL shared-runtime work and Capacitor integration remain later phases; they must not be pulled into the current phone-app or release blocks.
- GPU/Vulkan, simultaneous decode, multimodal support and speculative decoding remain outside the current production-capable embedded scope.

## Source links

- Capability milestones: [`roadmap.md`](roadmap.md)
- Target behavior: [`implementation-plan.md`](implementation-plan.md)
- Phone application architecture: [`features/phone-app-architecture.md`](features/phone-app-architecture.md)
- UX/UI acceptance criteria: [`harness-ux-ui-implementation-plan.md`](harness-ux-ui-implementation-plan.md)
- Generation behavior: [`generation-configuration-and-prompting-plan.md`](generation-configuration-and-prompting-plan.md)
- Harness 0.5 release gates: [`releases/harness-0.5.md`](releases/harness-0.5.md)
