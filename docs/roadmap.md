# Roadmap

Status: active
Document type: roadmap
Owner: repository
Canonical scope: roadmap.repository
Read when: selecting a capability milestone or understanding deferred product direction
Last reviewed: 2026-09-04

This file tracks capability-level milestones and remaining outcomes. Active branch/PR state and the next implementation task belong in [`current-state.md`](current-state.md).

Broad device/runtime production claims still require representative physical-device GGUF evidence.

## Milestone summary

| Milestone | Status | Remaining outcome |
| --- | --- | --- |
| Repository foundation and protected integration | Implemented | Maintain rulesets, release tagging and post-promotion ancestry sync |
| Functional embedded GGUF runtime | Implemented / device evidence pending | Representative device lifecycle, memory and JNI evidence |
| llama.cpp runtime efficiency and hardware execution | Planned / baseline-first | Qualify upstream evolution, improve CPU efficiency, then evaluate measured accelerator lanes |
| Telemetry, health, resources and benchmarks | Implemented / hardening pending | Device evidence and richer connected presentation |
| Dataset-based model evaluation | In progress | Dataset packs, deterministic evaluators, execution, persistence/comparison UI and physical evidence |
| Curated model distribution and installation | Implemented / device evidence pending | Remote download/install validation on representative phones |
| Qwen3.5-only product transition | In progress / certification pending | Q35-6 physical tuning, Q35-7 validation and Q35-8 exact-artifact certification |
| Connected Compose phone application | Partially complete | Remaining UDF migration, restoration, accessibility and responsive evidence |
| Model RAM residency and memory governance | Implemented / device calibration pending | MEM-7 measured profiles, MEM-8 certification and remaining product-facing controls |
| Reference-grade architecture hardening | In progress | RA-2/4/5/6/7/8/9/10/11 completion and cumulative certification |
| Public Consumer API and OMBRA reference consumer | In progress | OMB-6B identity plus OMB-8 quality and representative physical/release evidence |
| Native Android SDK integration | Planned | Stable consumer adapter over embedded contracts |
| Capacitor plugin | Planned | Thin bridge after native adapter stabilization |
| Cross-application diagnostics bridge | Planned | Signature-protected read/control surface |
| Shared Binder/AIDL runtime | Implemented / physical evidence pending | Formal ARM64/signer/resource evidence; automated lifecycle/fault matrix complete |

## Priority order across active plans

- **P0 — evidence/certification lane:** complete the remaining representative physical gates: LAS-07, OMB-6B/OMB-8, Q35-6/Q35-7, MEM-7/MEM-8, SR-6 and Harness 0.5 release evidence. The validated Harnex and RedactGuard baselines are already promoted to `main`. A new llama.cpp pin may preempt only for correctness/security.
- **P1 — safe parallel hardening:** upstream qualification, backend capability/effective-plan telemetry, prompt-token reuse and bounded CPU measurements may proceed with disjoint ownership. RA-4/5/7/9/10 and model evaluation remain separate owners.
- **P2 — post-CPU-evidence execution expansion:** Adreno OpenCL, kernel caching, K/V cache experiments, evaluation-only multi-sequence execution and deterministic device-plan evolution start after the CPU baseline is evidence-stable or on an explicit experimental lane.
- **P3 — research:** Hexagon/HTP and broader heterogeneous execution remain deferred until CPU/OpenCL ownership, packaging and evidence are understood.

Runtime-optimization task details are owned by [`llama-cpp-runtime-optimization-plan.md`](llama-cpp-runtime-optimization-plan.md).

## 1. Repository and release discipline

Implemented:

- reproducible Gradle and Android toolchain;
- formatting, Detekt, Lint, dependency and model-artifact guards;
- pinned `llama.cpp` and native host validation;
- `dev` integration and `main` stable promotion lines;
- scoped PR validation, cumulative `dev` validation and complete promotion validation;
- protected promotion, hotfix and forward-port rules in ADR 0008;
- reproducible Android packaging and launcher assets;
- 2026-09-04 validated Harnex/RedactGuard release promotion with `main -> dev` ancestry synchronization completed.

Remaining:

- maintain `dev`/`main` branch protection and rulesets;
- remove obsolete remote branches after audit;
- retain concise documentation governance and consistency guards;
- tie future release tags/artifacts to exact validated `main` commits;
- keep ADR 0008 post-promotion synchronization explicit in each release cycle.

## 2. Embedded runtime

Implemented:

- GGUF inspection and immutable SHA-256 identity;
- app-private content-addressed storage, verification and deduplication;
- explicit application/use-case/model resolution;
- opaque native model/context ownership;
- load, generate, stream, cancel, release and shutdown lifecycle;
- one-loaded-model and one-active-decode scheduling;
- request priority, queue cancellation and recoverable failures;
- compatible warm reuse, model-switch protection and Android memory-pressure handling;
- model-aware templates, structured input, exact token planning and lazy context sizing;
- output constraints, stop handling, seed policy and repetition protection.

Remaining:

- complete Q35-6/7/8 tuning, validation and certification while preserving model-family-neutral lifecycle contracts;
- product-facing RAM load/unload controls;
- representative device cancellation, memory, latency, throughput and thermal evidence;
- promote measured memory/runtime profiles only from compatible exact evidence;
- select performance policy from device evidence rather than desktop assumptions;
- execute bounded llama.cpp efficiency/hardware work without reopening validated Qwen3.5 behavior from generic API availability.

### llama.cpp efficiency and hardware execution

The pinned llama.cpp revision remains the Harness 0.5 CPU baseline unless correctness/security requires a controlled update. Newer revisions may be qualified in parallel, but promotion is explicit because Qwen tuning, memory calibration, OMBRA quality and release evidence are runtime-identity bound.

The staged sequence is:

1. qualify a candidate upstream revision and decide `PROMOTE` versus `DEFER` without floating dependencies;
2. expose truthful backend/device/effective-plan facts and remove avoidable CPU overhead such as duplicate prompt tokenization;
3. qualify recurrent/prefix/session reuse for Qwen3.5 as a correctness experiment, disabled in production unless exact-backend evidence passes;
4. extend CPU tuning only with bounded evidence-driven deltas;
5. after CPU evidence is stable, evaluate Adreno OpenCL, bounded compiled-kernel cache, K/V cache types and evaluation-only batching;
6. evolve RA-8 toward deterministic measured execution plans without uncontrolled online self-tuning;
7. keep Hexagon/HTP research-only until prior lanes are stable.

Canonical ledger: [`llama-cpp-runtime-optimization-plan.md`](llama-cpp-runtime-optimization-plan.md).

## 3. Model management and distribution

Implemented:

- strict curated catalog validation and application/use-case targeting;
- device compatibility filtering;
- secure HTTPS transfer, bounded redirects, size/storage checks and SHA-256 verification;
- opaque verified holding area;
- explicit installation with metadata-only GGUF inspection;
- non-destructive post-import verification;
- durable path-free installed metadata;
- connected download, install, import, selection, verification and protected removal;
- unified catalog/import/selection/runtime inventory;
- model details and deterministic ownership recovery;
- product catalog restricted to reviewed Qwen3.5 dense 0.8B/2B artifacts without deleting legacy installed bytes.

Remaining:

- complete Qwen3.5 candidate tuning, validation and exact-artifact certification;
- representative physical-device remote download/install evidence;
- `lastUsedAt` and final restart/reconciliation UI coverage;
- product RAM-residency actions separate from selection/storage;
- future administrator synchronization/trust-policy wiring only after the embedded path is stable.

Qwen sequence and exit gates are owned by [`qwen35/roadmap.md`](qwen35/roadmap.md).

## 4. Observability and developer controls

Implemented:

- bounded in-memory and Room stores;
- run lifecycle and request-correlated structured logs;
- privacy-safe timelines and typed error codes;
- queue, load, TTFT, prefill, decode, total, token and throughput metrics;
- effective generation metadata;
- health-suite orchestration, model integrity and generation sanity;
- Android memory and thermal snapshots;
- cache health and targeted repair;
- cold/warm benchmark keys, active baselines, retained immutable history and regression checks;
- connected phone Diagnostics surfaces.

Remaining:

- complete Diagnostics UDF migration;
- richer resource/benchmark-history visualization where source data supports it;
- complete unavailable/loading/error state tests;
- physical-device evidence for real values and lifecycle behavior;
- later signature-protected cross-application diagnostics;
- extend execution identity only with material backend/device/load/cache/reuse facts required by measured plans.

Dataset-based semantic model evaluation remains separate. EVAL-1 provides deterministic contracts/identity while dataset, evaluator, runner, persistence and Performance UI work proceeds under [`model-evaluation/README.md`](model-evaluation/README.md). Runtime benchmark ownership remains unchanged.

## 5. Connected Android application

Implemented:

- Compose/Material 3 with Overview, Playground, Applications, Performance, Models, Diagnostics and Settings;
- compact and expanded navigation;
- reproducible Harnex identity and shared design system;
- shared process runtime graph;
- real model management and Playground inference;
- Playground and Models ViewModel/UDF boundaries;
- typed Settings, request-timeline and model-detail routes;
- privacy-safe model inventory, diagnostics and validation reports;
- Google Play Internal Testing publication for the integrated candidate;
- stable-line promotion of the validated current baseline.

Remaining:

- migrate remaining Overview, Diagnostics and Settings state/effects from `MainActivity` where still owned there;
- complete process recreation, state restoration and Back-stack evidence;
- complete Compose state, screenshot, accessibility, large-font, landscape and expanded-layout matrices;
- finish representative-phone evidence beyond the automated API 35 matrix.

Acceptance details: [`harness-ux-ui-implementation-plan.md`](harness-ux-ui-implementation-plan.md); application boundary: [`features/phone-app-architecture.md`](features/phone-app-architecture.md).

## 6. Native and Capacitor integrations

Planned sequence:

1. stabilize/document the embedded native Android adapter;
2. expose lifecycle-safe client construction and app/use-case registration;
3. add consumer samples and compatibility tests;
4. implement a thin Capacitor plugin over the same native adapter;
5. keep prompt/output and native handles out of JavaScript persistence/diagnostics;
6. validate cancellation, Activity recreation and plugin shutdown on representative devices.

These integrations must not duplicate runtime policy or create a second model store.

## 7. Shared runtime, Consumer API and control plane

Integrated capabilities include Binder/AIDL shared runtime, version/feature negotiation, signer-aware access control, reconnect/client-death handling, durable logical jobs, packaged Consumer API boundaries and the OMBRA reference flow.

Automated lifecycle convergence is complete: Harnex source `6b34fe9f...` publishes Consumer SDK `0.1.0-alpha.10`, RedactGuard consumes it, and the complete API 35 Two-APK lifecycle/fault/serialization matrix is green. The validated Harnex and RedactGuard baselines are now promoted to their stable `main` lines. A representative manual RedactGuard run confirms the practical real-device flow. Formal ARM64/JNI/GGUF/memory/thermal/OEM evidence remains separate.

Remaining:

- SR-6/LAS-07 formal physical same-signer/ARM64/model/resource evidence where required;
- OMB-6B identity/launcher closure;
- OMB-8 exact-model quality and representative physical document-workflow evidence;
- signature-protected cross-application diagnostics only if separately justified;
- future release/version/signing documentation against exact promoted `main` identities.

Canonical sequencing: [`shared-runtime/roadmap.md`](shared-runtime/roadmap.md), [`shared-runtime/consumer-api/roadmap.md`](shared-runtime/consumer-api/roadmap.md), [`shared-runtime/consumer-api/pii-redactor/roadmap.md`](shared-runtime/consumer-api/pii-redactor/roadmap.md).

## 8. Reference-grade architecture hardening

Cross-cutting work makes architecture intent executable/testable without replacing capability owners.

Remaining outcomes:

- continue state-preserving decomposition only with closed lifecycle owners;
- complete typed failure/recovery and deterministic fault/race injection;
- finish fairness, cancellation-latency and slow/dead-consumer backpressure semantics;
- standardize correlation, reproducible execution identity and evidence-driven device policy using existing owners;
- finish backend conformance, shared-runtime physical ownership evidence and supply-chain/provenance hardening;
- certify cumulative architecture with automated and representative physical evidence.

Milestones and gates: [`reference-architecture-hardening-plan.md`](reference-architecture-hardening-plan.md); concise state: [`reference-architecture-hardening-progress.md`](reference-architecture-hardening-progress.md). RAM-residency work remains separate.

The llama.cpp optimization workstream consumes RA-7/8/9/10 rather than duplicating observability, device-policy, identity or conformance ownership.

## 9. Deferred capabilities

Deferred until the CPU embedded path and release evidence are stable:

- production-default Vulkan/GPU offload; experimental Adreno OpenCL remains separately staged;
- simultaneous decodes;
- speculative decoding;
- multimodal models;
- embeddings and rerankers;
- LoRA hot swapping;
- remote inference fallback;
- automatic model selection based on quality scoring.

## Release boundary

The active Harness 0.5.0 checklist is [`releases/harness-0.5.md`](releases/harness-0.5.md). The Harnex/RedactGuard automated integration baseline is now on the stable `main` lines; emulator/manual product acceptance still does not replace the remaining formal physical-device production evidence.
