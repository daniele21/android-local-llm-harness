# Current repository state

Status: active
Document type: current-state
Owner: repository
Canonical scope: state.repository
Read when: determining the integrated baseline, open blockers or next repository work block
Last reviewed: 2026-08-25

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

`apps/local-llm-phone-test` has connected Overview, Playground, Performance, Models, Diagnostics and Settings with real model/runtime/evaluation/observability sources. The repository-side product-experience realignment is complete: task-first Overview, Basic -> Advanced -> Expert Playground disclosure, evidence-map Diagnostics, task-backed Settings, adaptive/accessibility rules, fail-closed Performance decisions and ViewModel-owned generation guards for asynchronous Diagnostics actions are integrated and validated on the exact reconciled repository composition.

Remaining phone work is device/evidence or separately scoped capability work: process/back-stack restoration evidence, representative TalkBack/large-font/layout/screenshots, RAM warm-idle policy/controls and signed physical-GGUF evidence. CI/emulator validation does not satisfy those representative-device gates.

### Shared Android runtime

SR-0 through SR-5 are integrated. SR-6 repository-side release-evidence tooling is integrated, including packaged-client, same-signer/invalid-signer and process-death/reconnect fixtures.

The shared runtime is not production/release ready until the physical SR-6 evidence is executed on representative hardware. Canonical status and runbook: [`shared-runtime/roadmap.md`](shared-runtime/roadmap.md) and [`shared-runtime/sr6-release-evidence.md`](shared-runtime/sr6-release-evidence.md).

### Public Consumer API and OMBRA

CA-0 through CA-4 are integrated in `dev`. PR #104 completed the Binder v1.1 `consumer-api-v1` boundary with consumer AIDL/wire contracts, authenticated host mapping, Binder lifecycle/generation adapters, deterministic privacy/compatibility coverage and packaged release-AAR compilation evidence.

CA-5 is active through OMBRA. The repository-side document pipeline and product/quality preparation are substantially integrated:

- **OMB-0** — isolated PdfBox parser/export decision and runtime evidence through PR #106;
- **OMB-1** — pure domain/application workflow through PRs #107/#108;
- **OMB-2** — production PDF picker, extraction, typed failures and source cleanup through PR #154;
- **OMB-3** — deterministic prompt/schema/chunk planning and structured finding validation/orchestration through PRs #148/#202;
- **OMB-4** — host-owned `document-pii-detection` policy and packaged Binder Consumer API analysis adapter through PRs #144/#210;
- **OMB-5** — deterministic redaction, flattened PDF export and safe hidden/reveal projection through PRs #146/#157/#218;
- **OMB-6A** — OMBRA themes/tokens and reusable task/review components through PRs #145/#200/#220;
- **OMB-7A** — Compose Import -> Definitions -> Analysis -> Review-ready product flow through PR #232;
- **OMB-7B** — Review decisions/reveal/navigation, `CreateDocument` export, zero-PII handling, legacy Console retirement and pure-consumer dependency cleanup through PR #235;
- **OMB-7C/evidence closeout** — review privacy/accessibility evidence through PR #250 and the remaining reset/cancellation, portrait/landscape and code-owned screenshot matrix through PR #259;
- **OMB-8A** — deterministic synthetic quality corpus/scorer through PR #223, strengthened by PR #253 to active `ombra-pii-synthetic-v2` with 32 cases and at least five positive exact occurrences per supported category;
- **OMB-8B policy preparation** — pre-registered deterministic support-policy v1 through PR #252, pinned to the active corpus v2 identity/hash and required type set with fail-closed identity/category checks.

`apps/local-llm-console` no longer owns the retired Console model-management, observability, health, cache or raw inference surfaces and remains on public Consumer API/document/design-system boundaries. Repository-side OMB-7 product-state evidence is now integrated through PR #259. OMB-7 must still not be marked `DONE` until the approved OMB-6B production identity is integrated, as required by its exit gate.

**OMB-6B** remains independently open and review-gated in PR #248: the symbol candidate is not yet approved, and final wordmark/lockup plus adaptive/monochrome launcher assets are still pending. **OMB-8** now has both the active corpus v2 and pre-registered policy v1 integrated, but no supported-model/category claim is made until the exact reviewed Qwen3.5 artifacts are executed and the policy passes. Physical two-APK/device and release evidence also remain open.

Canonical milestone state: [`shared-runtime/consumer-api/roadmap.md`](shared-runtime/consumer-api/roadmap.md) and [`shared-runtime/consumer-api/pii-redactor/roadmap.md`](shared-runtime/consumer-api/pii-redactor/roadmap.md).

### Model evaluation

EVAL-0, EVAL-1 and EVAL-3 are complete. `evaluation/contracts` is the concrete backend-independent boundary for dataset/case/evaluator/sampling/run/result value semantics, deterministic SHA-256 identity, compatibility reasons and bounded evaluation failures. `evaluation/evaluators` freezes the six deterministic v1 scorer families and suite aggregation without an external LLM judge. These modules do not introduce a second runtime, model store, telemetry path or persistence implementation.

The dataset lane has integrated schema, bounded parsing, validation, canonical digest, atomic installation, registry/discovery, stratified sampling, preset resolution and reusable regression fixtures (`EVAL-D-01` through `D-09`). Android document import (`D-10`) is the next dataset slice. Runner preparation/case isolation, Room persistence/comparison and connected Performance UI continue in parallel. Performance fails closed until compatible aggregated evidence can support a model/configuration comparison. Canonical routing: [`model-evaluation/README.md`](model-evaluation/README.md).

This parallel capability does not replace the existing telemetry-derived benchmark engine and does not change the current OMBRA-focused repository sequencing.

## Open blockers

### 1. OMB-6B final identity review

Repository-side OMB-7 product-state evidence is integrated through PR #259. The remaining OMB-7 closure dependency is the separately owned OMB-6B production identity gate.

PR #248 contains a review-gated symbol candidate and deterministic safety validator, not an approved production identity. Before OMBRA can claim final app identity or mark OMB-7 complete:

- approve or revise the symbol candidate;
- freeze final wordmark/lockup decisions;
- generate deterministic adaptive and monochrome launcher assets from approved vector masters;
- add packaging checks without changing the accepted package/signing boundary.

Do not infer visual approval from a green identity-candidate workflow.

### 2. OMB-8 quality execution

The active 32-case corpus v2 from PR #253 and pre-registered support-policy v1 from PR #252 are integrated and identity-bound. The next quality gate is execution, not threshold design.

Before any Qwen3.5 model/category support claim:

- execute the exact active corpus on each reviewed supported artifact/configuration;
- evaluate aggregate and per-type precision/recall/F1 plus structured-completion and invalid-result/finding rates against policy v1;
- preserve exact artifact/preset/corpus identities in the evidence;
- fail closed on identity mismatch, missing required categories or any threshold failure;
- do not lower policy v1 to fit observed results; a changed policy requires a new version.

### 3. Physical Android evidence

Device-dependent tracks can share hardware sessions without conflating exit gates: phone UX (TalkBack/font/layout/restoration/real-GGUF), Q35-6 tuning, SR-6 Binder release evidence and OMB-8 quality/two-APK flows.

Do not promote phone UX to representative-device validated, Q35 profiles to `MEASURED`, publish the Binder client AAR or describe OMBRA/shared host transport as production-ready from CI/emulator evidence alone.

### 4. Follow-on validation and product hardening

Repository-side UX/UI implementation is complete. Remaining phone work is device/restoration evidence plus the separately scoped RAM warm-idle policy. After Q35-6, Q35-7 must run semantic/golden, context-boundary, cancellation, lifecycle, memory and thermal validation.

## Immediate next block

1. complete OMB-6B visual review and integrate approved deterministic launcher/identity assets;
2. execute OMBRA corpus v2 against reviewed Qwen3.5 artifacts using policy v1;
3. run OMB-8 physical same-signer two-APK import -> analysis -> review -> export/failure evidence;
4. keep Q35-6, SR-6 and phone UX device evidence parallel where hardware can be shared without conflating exit gates;
5. complete release privacy/security, packaging, versioning/signing and documentation checks against the exact build.

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
