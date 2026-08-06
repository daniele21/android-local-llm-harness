# Roadmap

Status: active
Document type: roadmap
Owner: repository
Last reviewed: 2026-08-06

This file tracks capability-level milestones and remaining outcomes. It does not own active branch names, pull-request narratives or the next implementation task; those belong in [`current-state.md`](current-state.md).

The repository remains not production-ready until representative physical-device GGUF evidence is complete.

## Milestone summary

| Milestone | Status | Remaining outcome |
| --- | --- | --- |
| Repository foundation and protected integration | Implemented | Confirm the repository-level `dev` ruleset before release |
| Functional embedded GGUF runtime | Implemented / device evidence pending | Representative device lifecycle, memory and JNI evidence |
| Telemetry, health, resources and benchmarks | Implemented / hardening pending | Device evidence and richer connected presentation |
| Curated model distribution and installation | Implemented / device evidence pending | Real remote download/install validation on representative phones |
| Connected Compose phone application | Partially complete | Remaining UDF migration, restoration, accessibility and responsive evidence |
| Model RAM residency and warm-idle eviction | Planned | Product controls, TTL implementation and tests |
| Native Android SDK integration | Planned | Stable consumer adapter over the embedded contracts |
| Capacitor plugin | Planned | Thin bridge after native adapter stabilization |
| Cross-application diagnostics bridge | Planned | Signature-protected read/control surface |
| Shared Binder/AIDL runtime | Future | Central model/runtime coordination after embedded evidence |

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

- confirm `dev` branch protection through repository settings;
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

- explicit product-facing RAM load/unload controls;
- monotonic warm-idle TTL with cancellation, rearming, pinning and unload reasons;
- representative physical-device validation of cancellation, memory stability, latency, throughput and thermal behavior;
- performance policy selection based on device evidence rather than desktop assumptions.

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
- model details and deterministic ownership recovery.

Remaining:

- representative physical-device remote download and installation evidence;
- `lastUsedAt` and final restart/reconciliation UI coverage;
- product RAM-residency actions separate from selection and storage;
- future administrator synchronization and trust-policy wiring only after the current embedded distribution path is stable.

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
- later signature-protected diagnostics bridge for cross-application inspection.

## 5. Connected Android application

Implemented:

- Compose/Material 3 surface with Overview, Playground, Models, Diagnostics and Settings;
- compact and expanded navigation shell;
- reproducible Harness identity and shared design system;
- shared process runtime graph;
- real model management and Playground inference;
- Playground and Models ViewModel/UDF boundaries;
- typed Settings, request-timeline and model-detail routes;
- privacy-safe model inventory, diagnostics and validation reports.

Remaining:

- migrate Overview, Diagnostics and Settings state/effects from `MainActivity`;
- complete process recreation, state restoration and Back-stack evidence;
- complete Compose state, screenshot, accessibility, large-font, landscape and expanded-layout matrices;
- validate remote distribution, inference, cancellation and recovery on representative phones;
- publish the signed candidate through Google Play Internal Testing.

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

## 7. Shared runtime and control plane

Future work after the embedded path is release-ready:

- signature-protected diagnostics bridge;
- stable serializable transport contracts;
- Binder/AIDL runtime service;
- centralized model-file deduplication and memory coordination;
- explicit application authorization and lifecycle ownership;
- migration path from in-process transport without changing model-binding semantics.

The shared service is not part of Harness 0.5.0.

## 8. Deferred capabilities

Deferred until the CPU embedded path and release evidence are stable:

- production-default Vulkan/GPU offload;
- simultaneous decodes;
- speculative decoding;
- multimodal models;
- embeddings and rerankers;
- LoRA hot swapping;
- remote inference fallback;
- automatic model selection based on quality scoring.

## Release boundary

The active Harness 0.5.0 checklist is [`releases/harness-0.5.md`](releases/harness-0.5.md). Emulator, host-native and simulated acceptance evidence support merge readiness but do not satisfy physical-device production readiness.
