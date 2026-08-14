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

CA-0 through CA-4 are integrated in `dev`. PR #104 completed the Binder v1.1 `consumer-api-v1` boundary with consumer AIDL/wire contracts, authenticated host mapping, Binder lifecycle/generation adapters, deterministic privacy/compatibility coverage and packaged release-AAR compilation evidence.

CA-5 is now active through OMBRA OMB-0. The first slice freezes product/parser/export/schema/use-case decisions before domain/UI implementation. Canonical milestone state: [`shared-runtime/consumer-api/roadmap.md`](shared-runtime/consumer-api/roadmap.md) and [`shared-runtime/consumer-api/pii-redactor/roadmap.md`](shared-runtime/consumer-api/pii-redactor/roadmap.md).

### Model evaluation planning

EVAL-0 is complete as a documentation/architecture milestone. The dataset-based model-evaluation capability now has a canonical target, dependency graph, detailed task ledgers and maintenance rules under [`model-evaluation/README.md`](model-evaluation/README.md).

EVAL-1 Contracts and identity is `READY`; `EVAL-C-01` is the first implementation task. This work does not replace the existing telemetry-derived benchmark engine. Host-side model-evaluation implementation may proceed in parallel with OMBRA and the physical Q35-6/SR-6 evidence tracks, subject to normal development capacity.

## Open blockers

### 1. OMBRA OMB-0 decisions and spikes

Complete the bounded parser/export decision slice before growing application code. The selected path must preserve OMBRA as a pure consumer: local PDF access/extraction/export, no model/runtime ownership, fixed structured-output semantics and mandatory human review.

### 2. Physical Android evidence

Two device-dependent tracks remain open and can share a representative hardware session:

- **Q35-6** — run the controlled Qwen3.5 0.8B/2B tuning matrix, collect cold/warm timing, throughput, PSS, available-memory and thermal evidence, then select measured defaults;
- **SR-6** — run release-like same-signer Binder evidence, invalid-signer denial, process-death/reconnect and matching Binder-vs-in-process overhead evidence.

Do not promote Q35 profiles to `MEASURED`, publish the Binder client AAR or describe the shared host/public transport as production-ready from CI/emulator evidence alone.

### 3. Follow-on validation and product hardening

After Q35-6, Q35-7 must run semantic/golden, context-boundary, cancellation, lifecycle, memory and thermal validation. Remaining phone work includes UDF completion, RAM warm-idle TTL policy, accessibility/responsive coverage and signed release evidence.

## Immediate next block

1. complete OMB-0A product/parser/export/schema/use-case decisions and bounded spikes;
2. start OMB-1 pure Android-independent domain/state only after OMB-0 architecture choices are reviewable;
3. keep OMB-4 real Consumer API integration behind the accepted OMBRA domain/extraction/composition boundaries;
4. keep Q35-6 and SR-6 physical evidence as the parallel release-readiness track.

Model evaluation is planned and ready to begin at EVAL-C-01, but this addition does not replace the repository's current OMBRA-focused immediate block.

## Source links

- Capability roadmap: [`roadmap.md`](roadmap.md)
- Model evaluation plan: [`model-evaluation/README.md`](model-evaluation/README.md)
- Consumer API roadmap: [`shared-runtime/consumer-api/roadmap.md`](shared-runtime/consumer-api/roadmap.md)
- CA-4 Binder specification: [`shared-runtime/consumer-api/ca4-binder-protocol.md`](shared-runtime/consumer-api/ca4-binder-protocol.md)
- OMBRA roadmap: [`shared-runtime/consumer-api/pii-redactor/roadmap.md`](shared-runtime/consumer-api/pii-redactor/roadmap.md)
- Shared runtime roadmap: [`shared-runtime/roadmap.md`](shared-runtime/roadmap.md)
- SR-6 evidence runbook: [`shared-runtime/sr6-release-evidence.md`](shared-runtime/sr6-release-evidence.md)
- Qwen3.5 status: [`qwen35/README.md`](qwen35/README.md)
- Harness 0.5 release gates: [`releases/harness-0.5.md`](releases/harness-0.5.md)
