# Current repository state

Status: active
Document type: current-state
Owner: repository
Canonical scope: state.repository
Read when: determining the integrated baseline, open blockers or next repository work block
Last reviewed: 2026-08-15

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

CA-5 is active through OMBRA. The repository-side document pipeline is substantially integrated:

- **OMB-0** — isolated PdfBox parser/export decision and runtime evidence through PR #106;
- **OMB-1** — pure domain/application workflow through PRs #107/#108;
- **OMB-2** — production PDF picker, extraction, typed failures and source cleanup through PR #154;
- **OMB-3** — deterministic prompt/schema/chunk planning and structured finding validation/orchestration through PRs #148/#202;
- **OMB-4** — host-owned `document-pii-detection` policy and packaged Binder Consumer API analysis adapter through PRs #144/#210;
- **OMB-5** — deterministic redaction, flattened PDF export and safe hidden/reveal projection through PRs #146/#157/#218;
- **OMB-6A** — OMBRA themes/tokens and reusable task/review components through PRs #145/#200/#220;
- **OMB-7A** — Compose Import -> Definitions -> Analysis -> Review-ready product flow through PR #232;
- **OMB-8A** — deterministic synthetic quality corpus and exact-occurrence scorer through PR #223.

The active product-closeout candidate is **OMB-7B / PR #235**: Review decisions/reveal/navigation, `CreateDocument` export, zero-PII flow and retirement of the legacy Console control-plane surfaces. Its cleanup descendant **PR #236** removes direct `models:model-store` and `observability:*` dependencies so `apps/local-llm-console` becomes a pure Consumer API reference app. This work is not part of the integrated `dev` baseline until the exact candidate head is green and merged.

**OMB-6B** remains independently open and review-gated in PR #248: the symbol candidate is not yet approved, and final wordmark/lockup plus adaptive/monochrome launcher assets are still pending. **OMB-8** is also active only at the preparation level: corpus/scoring exists, while quality thresholds, representative model execution, physical evidence and release checks remain open.

Canonical milestone state: [`shared-runtime/consumer-api/roadmap.md`](shared-runtime/consumer-api/roadmap.md) and [`shared-runtime/consumer-api/pii-redactor/roadmap.md`](shared-runtime/consumer-api/pii-redactor/roadmap.md).

### Model evaluation

EVAL-0, EVAL-1 and EVAL-3 are complete. `evaluation/contracts` is the concrete backend-independent boundary for dataset/case/evaluator/sampling/run/result value semantics, deterministic SHA-256 identity, compatibility reasons and bounded evaluation failures. `evaluation/evaluators` freezes the six deterministic v1 scorer families and suite aggregation without an external LLM judge. These modules do not introduce a second runtime, model store, telemetry path or persistence implementation.

The dataset lane has integrated schema, bounded parsing, validation, canonical digest, atomic installation, registry/discovery, stratified sampling, preset resolution and reusable regression fixtures (`EVAL-D-01` through `D-09`). Android document import (`D-10`) is the next dataset slice. Runner preparation/case isolation, Room persistence/comparison and connected Performance UI continue in parallel. Canonical state and dependency routing: [`model-evaluation/README.md`](model-evaluation/README.md).

This parallel capability does not replace the existing telemetry-derived benchmark engine and does not change the current OMBRA-focused repository sequencing.

## Open blockers

### 1. OMBRA OMB-7B pure-consumer product closeout

Close the exact-head OMB-7B gate before claiming CA-5 complete. The candidate must preserve the full local workflow while eliminating the retired Console model-management, observability, health, cache and raw inference dependencies. The reference app may depend on public contracts, the Binder Consumer client, its document/PDF implementation and the shared design system, but it must not regain direct runtime/model-control ownership.

The current closeout also needs the remaining product state-matrix evidence: review/export failures, cancellation/reset, semantics, adaptive/large-font behavior and representative screenshots where owned by OMB-7. Final app identity remains gated separately by OMB-6B visual approval.

### 2. OMB-6B final identity review

PR #248 contains a review-gated symbol candidate and deterministic safety validator, not an approved production identity. Before OMBRA can claim final app identity:

- approve or revise the symbol candidate;
- freeze final wordmark/lockup decisions;
- generate deterministic adaptive and monochrome launcher assets from approved vector masters;
- add packaging checks without changing the accepted package/signing boundary.

Do not infer visual approval from a green identity-candidate workflow.

### 3. Physical Android evidence

Three device-dependent tracks remain open and can share representative hardware sessions where appropriate:

- **Q35-6** — run the controlled Qwen3.5 0.8B/2B tuning matrix, collect cold/warm timing, throughput, PSS, available-memory and thermal evidence, then select measured defaults;
- **SR-6** — run release-like same-signer Binder evidence, invalid-signer denial, process-death/reconnect and matching Binder-vs-in-process overhead evidence;
- **OMB-8** — run the OMBRA corpus and full two-APK import -> analysis -> review -> export/failure scenarios on the exact supported build, then record quality and privacy-safe release evidence.

Do not promote Q35 profiles to `MEASURED`, publish the Binder client AAR or describe OMBRA/shared host transport as production-ready from CI/emulator evidence alone.

### 4. Follow-on validation and product hardening

After Q35-6, Q35-7 must run semantic/golden, context-boundary, cancellation, lifecycle, memory and thermal validation. Remaining phone work includes UDF completion, RAM warm-idle TTL policy, accessibility/responsive coverage and signed release evidence.

## Immediate next block

1. finish the exact-head OMB-7B validation, fold the pure-consumer cleanup into PR #235 and integrate only after repository/PDF gates are green;
2. keep OMB-6B final identity review/assets as an independent parallel lane; do not wire an unapproved launcher candidate into production;
3. advance OMB-8 from the already-integrated corpus to accepted quality thresholds and representative two-APK/device evidence only after the product flow is stable;
4. keep Q35-6 and SR-6 physical evidence as the parallel release-readiness track.

Model-evaluation work may proceed in parallel when ownership is disjoint, without changing these OMBRA dependency and evidence gates.

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
