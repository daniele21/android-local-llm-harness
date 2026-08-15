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

### Public Consumer API and OMBRA

CA-0 through CA-4 are integrated in `dev`. PR #104 completed the Binder v1.1 `consumer-api-v1` boundary with consumer AIDL/wire contracts, authenticated host mapping, Binder lifecycle/generation adapters, deterministic privacy/compatibility coverage and packaged release-AAR compilation evidence.

CA-5 is active through OMBRA. OMB-0 is integrated through PR #106 with the isolated PdfBox parser/export decision and runtime evidence. OMB-1A and OMB-1B are integrated through PRs #107 and #108, closing the pure domain/application-state gate with Android-independent models, immutable workflow state, focused reducer transitions, replaceable async ports, sensitive in-memory task storage and fake end-to-end orchestration. OMB-2 is now the active implementation block for production PDF import/extraction behind the application port. Canonical milestone state: [`shared-runtime/consumer-api/roadmap.md`](shared-runtime/consumer-api/roadmap.md) and [`shared-runtime/consumer-api/pii-redactor/roadmap.md`](shared-runtime/consumer-api/pii-redactor/roadmap.md).

### Model evaluation

EVAL-0, EVAL-1 and EVAL-3 are complete. `evaluation/contracts` is the concrete backend-independent boundary for dataset/case/evaluator/sampling/run/result value semantics, deterministic SHA-256 identity, compatibility reasons and bounded evaluation failures. `evaluation/evaluators` freezes the six deterministic v1 scorer families and suite aggregation without an external LLM judge. These modules do not introduce a second runtime, model store, telemetry path or persistence implementation.

The dataset lane has integrated schema, bounded parsing, validation, canonical digest, atomic installation, registry/discovery, stratified sampling and preset resolution (`EVAL-D-01` through `D-08`). Regression fixtures (`D-09`) and Android document import (`D-10`) are the next independent slices. Runner preparation/case isolation, Room persistence/comparison and connected Performance UI continue in parallel. Canonical state and dependency routing: [`model-evaluation/README.md`](model-evaluation/README.md).

This parallel capability does not replace the existing telemetry-derived benchmark engine and does not change the current OMBRA-focused repository sequencing.

## Open blockers

### 1. OMBRA OMB-2 production PDF import and extraction

Complete the production extraction boundary before growing analysis or UI code. The active slice must keep raw URI/PDF ownership local to OMBRA, route extraction through the reviewed isolated PdfBox process, produce deterministic page/block source mapping, fail closed with typed outcomes and prove cancellation/resource cleanup. OMB-2 must also wire the PDF-only `OpenDocument` capability and prove that reset releases source capability state and sensitive task data.

### 2. Physical Android evidence

Two device-dependent tracks remain open and can share a representative hardware session:

- **Q35-6** — run the controlled Qwen3.5 0.8B/2B tuning matrix, collect cold/warm timing, throughput, PSS, available-memory and thermal evidence, then select measured defaults;
- **SR-6** — run release-like same-signer Binder evidence, invalid-signer denial, process-death/reconnect and matching Binder-vs-in-process overhead evidence.

Do not promote Q35 profiles to `MEASURED`, publish the Binder client AAR or describe the shared host/public transport as production-ready from CI/emulator evidence alone.

### 3. Follow-on validation and product hardening

After Q35-6, Q35-7 must run semantic/golden, context-boundary, cancellation, lifecycle, memory and thermal validation. Remaining phone work includes UDF completion, RAM warm-idle TTL policy, accessibility/responsive coverage and signed release evidence.

## Immediate next block

1. complete OMB-2A production PDF source capability, extractor adapter, deterministic segmentation and emulator/resource-cleanup evidence;
2. close the remaining OMB-2 picker/reset/typed-failure exit-gate work before starting OMB-3 analysis composition;
3. keep OMB-4 real Consumer API integration behind the accepted OMBRA domain/extraction/composition boundaries;
4. keep Q35-6 and SR-6 physical evidence as the parallel release-readiness track.

Model-evaluation work may proceed in parallel when ownership is disjoint, without changing this immediate OMBRA sequencing.

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
