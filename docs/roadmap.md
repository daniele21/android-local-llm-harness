# Roadmap

Status: active
Document type: roadmap
Owner: repository
Canonical scope: roadmap.repository
Read when: selecting a capability milestone or understanding deferred product direction
Last reviewed: 2026-09-04

This file tracks capability-level milestones and remaining outcomes. It does not own active branch names, pull-request narratives or the next implementation task; those belong in [`current-state.md`](current-state.md).

The repository remains not production-ready for broad device/runtime claims until representative physical-device GGUF evidence is complete.

## Milestone summary

| Milestone | Status | Remaining outcome |
| --- | --- | --- |
| Repository foundation and protected integration | Implemented | Complete controlled `dev -> main` release promotion and keep rulesets current |
| Functional embedded GGUF runtime | Implemented / device evidence pending | Representative device lifecycle, memory and JNI evidence |
| llama.cpp runtime efficiency and hardware execution | Planned / baseline-first | Qualify upstream evolution, improve CPU efficiency, then evaluate measured accelerator lanes |
| Telemetry, health, resources and benchmarks | Implemented / hardening pending | Device evidence and richer connected presentation |
| Dataset-based model evaluation | In progress | Dataset packs, deterministic evaluators, execution, persistence/comparison UI and physical-device evidence |
| Curated model distribution and installation | Implemented / device evidence pending | Real remote download/install validation on representative phones |
| Qwen3.5-only product transition | In progress / certification pending | Q35-6 physical tuning, Q35-7 validation and Q35-8 exact-artifact certification |
| Connected Compose phone application | Partially complete | Remaining UDF migration, restoration, accessibility and responsive evidence |
| Model RAM residency and memory governance | Implemented / device calibration pending | MEM-7 measured profiles, MEM-8 certification and remaining product-facing controls |
| Reference-grade architecture hardening | In progress | RA-2/4/5/6/7/8/9/10/11 completion and cumulative certification |
| Public Consumer API and OMBRA reference consumer | In progress | OMB-6B identity plus OMB-8 quality and representative physical/release evidence |
| Native Android SDK integration | Planned | Stable consumer adapter over the embedded contracts |
| Capacitor plugin | Planned | Thin bridge after native adapter stabilization |
| Cross-application diagnostics bridge | Planned | Signature-protected read/control surface |
| Shared Binder/AIDL runtime | Implemented / physical evidence pending | Formal representative ARM64/signer/resource evidence; automated lifecycle/fault matrix is complete |

## Priority order across active plans

- **P0 — release/evidence lane:** promote the validated Harnex and RedactGuard baselines to `main`, then complete the representative physical gates that genuinely remain: LAS-07, OMB-6B/OMB-8, Q35-6/Q35-7, MEM-7/MEM-8, SR-6 and Harness 0.5 release evidence. A new llama.cpp pin or optimization may preempt this lane only for a correctness or security blocker.
- **P1 — safe parallel hardening:** upstream qualification, backend capability/effective-plan telemetry, prompt-token reuse and bounded CPU-side measurements may proceed when ownership is disjoint. Reference-architecture RA-4/5/7/9/10 and model-evaluation work remain parallel owners rather than being duplicated here.
- **P2 — post-CPU-evidence execution expansion:** Adreno OpenCL, OpenCL kernel caching, K/V cache type experiments, evaluation-only multi-sequence execution and deterministic device-plan evolution begin only after the CPU baseline is evidence-stable or on an explicitly non-release experimental lane.
- **P3 — research:** Hexagon/HTP and broader heterogeneous execution remain deferred until CPU/OpenCL ownership, packaging and evidence are understood.

Detailed IDs, dependencies and acceptance rules for the runtime-optimization lane are owned by [`llama-cpp-runtime-optimization-plan.md`](llama-cpp-runtime-optimization-plan.md).

## 1. Repository and release discipline

Implemented:

- reproducible Gradle and Android toolchain;
- formatting, Detekt, Lint, dependency and model-artifact guards;
- pinned `llama.cpp` and native host validation;
- `dev` as ordinary integration line and `main` as stable promotion line;
- scoped pull-request validation, cumulative `dev` validation and complete promotion validation;
- protected promotion, hotfix and forward-port rules documented in ADR 0008;
- reproducible Android packaging and launcher assets.

Remaining:

- complete the current FULL-validated `dev -> main` promotion while preserving valid main-only hotfix history;
- confirm and maintain `dev`/`main` branch protection through repository settings;
- remove obsolete remote branches after audit;
- retain concise documentation governance and consistency guards;
- keep release tags and artifacts tied to an exact validated `main` commit.

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
- explicit product-facing RAM load/unload controls;
- representative physical-device validation of cancellation, memory stability, latency, throughput and thermal behavior;
- promote measured memory/runtime profiles only from exact compatible evidence;
- performance policy selection based on device evidence rather than desktop assumptions;
- execute the bounded llama.cpp efficiency/hardware workstream without reopening validated Qwen3.5 behavior from generic API availability alone.

### llama.cpp efficiency and hardware execution

The current pinned llama.cpp revision remains the Harness 0.5 CPU baseline unless a correctness/security blocker requires a controlled update. Newer upstream revisions may be qualified in parallel, but promotion is explicit because Qwen tuning, memory calibration, OMBRA quality evidence and release evidence are runtime-identity bound.

The staged sequence is:

1. qualify a candidate upstream revision and decide `PROMOTE` versus `DEFER` without floating dependencies;
2. expose truthful backend/device/effective-plan facts and remove avoidable CPU overhead such as duplicate prompt tokenization;
3. qualify recurrent/prefix/session reuse for Qwen3.5 as a correctness experiment while keeping production reuse disabled unless exact-backend evidence passes;
4. extend CPU tuning only with bounded evidence-driven deltas rather than an unbounded Cartesian parameter matrix;
5. after the CPU path is evidence-stable, evaluate Adreno OpenCL, its bounded compiled-kernel cache, K/V cache types and evaluation-only batching;
6. evolve RA-8 toward deterministic measured execution plans; do not add uncontrolled online self-tuning;
7. keep Hexagon/HTP research-only until the prior lanes are stable.

Canonical target and task ledger: [`llama-cpp-runtime-optimization-plan.md`](llama-cpp-runtime-optimization-plan.md).

## 3. Model management and distribution

Implemented:

- strict curated catalog validation and application/use-case targeting;
- device compatibility filtering;
- secure HTTPS transfer, bounded redirects, size/storage checks and SHA-256 verification;
- opaque verified holding area;
- explicit installation with metadata-only GGUF inspection;
- non-destructive post-import verification behavior;
- durable path-free installed metadata;
- connected download, install, import, selection, verification and protected removal;
- unified catalog/import/selection/runtime inventory;
- model details and deterministic ownership recovery;
- closed product catalog restricted to reviewed Qwen3.5 dense 0.8B/2B artifacts without deleting legacy installed bytes.

Remaining:

- complete Qwen3.5 candidate tuning, validation and exact-artifact certification;
- representative physical-device remote download and installation evidence;
- `lastUsedAt` and final restart/reconciliation UI coverage;
- product RAM-residency actions separate from selection and storage;
- future administrator synchronization and trust-policy wiring only after the current embedded distribution path is stable.

The repository roadmap intentionally does not duplicate the Qwen milestone matrix. Sequence, states and exit gates are owned by [`qwen35/roadmap.md`](qwen35/roadmap.md).

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
- richer resource and benchmark-history visualization where source data supports it;
- complete unavailable/loading/error state tests;
- physical-device evidence for real values and lifecycle behavior;
- later signature-protected diagnostics bridge for cross-application inspection;
- extend execution identity only with material backend/device/load/cache/reuse facts required by measured llama.cpp plans.

Dataset-based semantic model evaluation is a separate active control-plane capability. EVAL-1 provides deterministic contracts/identity while dataset, evaluator, runner, persistence and Performance UI lanes proceed independently under [`model-evaluation/README.md`](model-evaluation/README.md). The existing benchmark engine remains the owner of telemetry-derived runtime baselines and regression health.

## 5. Connected Android application

Implemented:

- Compose/Material 3 surface with Overview, Playground, Applications, Performance, Models, Diagnostics and Settings;
- compact and expanded navigation shell;
- reproducible Harnex identity and shared design system;
- shared process runtime graph;
- real model management and Playground inference;
- Playground and Models ViewModel/UDF boundaries;
- typed Settings, request-timeline and model-detail routes;
- privacy-safe model inventory, diagnostics and validation reports;
- repository-owned Google Play Internal Testing publication for the integrated current candidate.

Remaining:

- migrate remaining Overview, Diagnostics and Settings state/effects from `MainActivity` where still owned there;
- complete process recreation, state restoration and Back-stack evidence;
- complete Compose state, screenshot, accessibility, large-font, landscape and expanded-layout matrices;
- finish representative-phone evidence for distribution, inference, cancellation, resource behavior and recovery beyond the automated API 35 matrix.

Focused acceptance criteria remain in [`harness-ux-ui-implementation-plan.md`](harness-ux-ui-implementation-plan.md), and the durable application boundary is documented in [`features/phone-app-architecture.md`](features/phone-app-architecture.md).

## 6. Native and Capacitor integrations

Planned sequence:

1. stabilize and document the embedded native Android adapter;
2. expose lifecycle-safe client construction and application/use-case registration;
3. add consumer samples and compatibility tests;
4. implement a thin Capacitor plugin over the same native adapter;
5. keep prompt/output and native handles out of JavaScript persistence and diagnostics;
6. validate cancellation, Activity recreation and plugin shutdown on representative devices.

These integrations must not duplicate runtime policy or create a second model store.

## 7. Shared runtime, Consumer API and control plane

Integrated repository-side capabilities include the Binder/AIDL shared runtime, version/feature negotiation, signer-aware access control, reconnect/client-death handling, durable logical jobs, packaged Consumer API boundaries and the OMBRA reference consumer flow.

The final automated lifecycle convergence is complete: Harnex `dev@6b34fe9f...` publishes Consumer Android SDK `0.1.0-alpha.10`, RedactGuard consumes that SDK, and the complete API 35 Two-APK lifecycle/fault/serialization matrix is green. A representative manual RedactGuard run also confirms the practical real-device product flow works. These facts do not replace formal ARM64/JNI/GGUF/memory/thermal/OEM evidence.

Remaining:

- SR-6/LAS-07 formal physical same-signer/ARM64/model/resource evidence where required by the claim;
- OMB-6B approved identity/launcher closure;
- OMB-8 exact-model quality execution and representative physical document workflow evidence;
- signature-protected cross-application diagnostics only when its separate read/control contract is justified;
- release/version/signing documentation against the exact promoted `main` build.

Canonical sequencing remains in [`shared-runtime/roadmap.md`](shared-runtime/roadmap.md), [`shared-runtime/consumer-api/roadmap.md`](shared-runtime/consumer-api/roadmap.md) and [`shared-runtime/consumer-api/pii-redactor/roadmap.md`](shared-runtime/consumer-api/pii-redactor/roadmap.md). The shared runtime and Consumer API must not duplicate model, scheduler, retry or memory policy from runtime core.

## 8. Reference-grade architecture hardening

Cross-cutting work makes existing architecture intent executable and independently testable without replacing capability-specific owners.

Remaining outcomes:

- continue state-preserving runtime decomposition only where closed lifecycle owners remain intact;
- complete typed failure/recovery and deterministic fault/race injection;
- finish fairness, cancellation-latency and slow/dead-consumer backpressure semantics;
- standardize correlation, reproducible execution identity and evidence-driven device policy while reusing existing observability, ModelStore and memory owners;
- finish backend conformance, shared-runtime physical ownership evidence and supply-chain/provenance hardening;
- certify the cumulative architecture with automated and representative physical-device evidence.

Detailed milestone IDs, dependencies, parallel lanes and exit gates are owned by [`reference-architecture-hardening-plan.md`](reference-architecture-hardening-plan.md); concise state belongs in [`reference-architecture-hardening-progress.md`](reference-architecture-hardening-progress.md). Existing RAM-residency work remains separate and integrates through the lifecycle/scheduler/resource seams defined there.

The llama.cpp optimization workstream consumes RA-7/8/9/10 rather than creating parallel observability, device-policy, identity or conformance owners.

## 9. Deferred capabilities

Deferred until the CPU embedded path and release evidence are stable:

- production-default Vulkan/GPU offload; experimental Adreno OpenCL is separately staged under the llama.cpp optimization workstream and does not change this production-default gate;
- simultaneous decodes;
- speculative decoding;
- multimodal models;
- embeddings and rerankers;
- LoRA hot swapping;
- remote inference fallback;
- automatic model selection based on quality scoring.

## Release boundary

The active Harness 0.5.0 checklist is [`releases/harness-0.5.md`](releases/harness-0.5.md). The current Harnex/RedactGuard automated integration baseline is release-promotion ready, but emulator and manual-product acceptance evidence do not by themselves satisfy the remaining formal physical-device production claims.
