# Harness Android UX/UI target specification

Status: active
Document type: target-specification
Owner: apps/local-llm-phone-test
Canonical scope: target.phone-ux
Read when: changing connected-phone screen behavior, interaction patterns or UX acceptance criteria
Last reviewed: 2026-08-06

## Purpose

This document defines the product behavior and acceptance criteria for the connected Android Harness application. Current implementation status belongs in [`harness-ux-ui-implementation-progress.md`](harness-ux-ui-implementation-progress.md) and [`current-state.md`](current-state.md).

The primary application is `apps/local-llm-phone-test`, branded as **Harness — Local AI Console**. It is the first connected product surface for model management, local inference, diagnostics and physical-device validation.

The implementation must:

- keep inference and GGUF data on-device;
- preserve the shared runtime, model-store, cancellation, cleanup and integrity boundaries;
- avoid introducing Binder/AIDL or cross-application runtime ownership in this phase;
- use real source-backed state and never substitute illustrative mockup values;
- support compact phones, larger devices, portrait and landscape;
- support dynamic text, TalkBack, deterministic focus and low-motion operation;
- keep prompt and generated output outside normal telemetry and persistence.

The durable application architecture is documented in [`features/phone-app-architecture.md`](features/phone-app-architecture.md).

## Product structure

Top-level destinations:

1. Overview
2. Playground
3. Models
4. Diagnostics
5. Settings

Compact widths use bottom navigation. Medium and expanded widths use a navigation rail. Detail destinations hide top-level navigation and provide deterministic Back behavior.

## Global presentation rules

- `Harness` is the product brand; `Local AI Console` is the descriptor.
- GGUF is the only supported model format in this phase.
- Model names, memory, temperatures, throughput and latency must be real values or explicit unavailable/not-run states.
- Model installation, selection and RAM residency must be visually and behaviorally distinct.
- Health, benchmark, resource and validation actions are explicit; navigation and refresh do not start work implicitly.
- Destructive storage actions require confirmation and must explain what is retained or removed.
- No UI surface exposes private filesystem paths, document URIs, download URLs, signed URLs, prompts, generated output or arbitrary backend exception messages.

## Shared shell and navigation

The application shell must provide:

- stable top-level route identity;
- compact bottom navigation and expanded navigation rail;
- detail-aware top bars and deterministic Back behavior;
- state restoration that does not persist sensitive content;
- system-bar and display-cutout handling;
- no runtime, download, health, resource or benchmark side effects from route changes;
- process recreation behavior covered by deterministic tests.

Detail routes include:

- model details;
- request timeline;
- Settings privacy, storage, build and developer tools;
- physical validation.

Route arguments must use bounded opaque identifiers.

## Overview

Overview must summarize current source-backed state without becoming a second control plane.

Required content:

- selected model and installed/selected/loaded distinction;
- runtime state and active work;
- latest real Playground result metrics when available;
- resource-pressure summary when captured;
- latest run or diagnostic status;
- direct navigation to the relevant detailed surface.

Acceptance criteria:

- unavailable sources are explicit;
- no action executes during composition or refresh;
- active-operation status remains consistent with Playground, Models and Diagnostics;
- state derives from shared product state rather than Activity-local mirrors.

## Playground

The Playground performs real local generation through the shared runtime.

Required behavior:

- process-memory-only prompt and generated output;
- explicit model requirement and preparation state;
- versioned presets and custom sampling controls;
- effective configuration derived from real runtime planning;
- queued, preparing, prefill, generating, cancelling and terminal states;
- cooperative cancellation;
- bounded streaming presentation and smart auto-scroll that yields to the reader;
- terminal metrics and privacy-safe errors;
- cleanup after completion, failure or cancellation;
- navigation that does not cancel active work unless the user explicitly requests it.

Acceptance criteria:

- the UI does not duplicate runtime validation or prompt policy;
- complete, failed, cancelled and cleanup-failed paths are deterministic;
- double taps and concurrent requests are guarded;
- warm reuse and safe model switching remain observable;
- Compose semantics cover all major states;
- physical-device evidence uses a real GGUF.

Generation contracts and constraints are documented in [`generation-configuration-and-prompting-plan.md`](generation-configuration-and-prompting-plan.md).

## Models

Models presents one reconciled view of:

- curated catalog releases;
- installed catalog metadata;
- external imported GGUF models;
- active selection;
- runtime-loaded ownership;
- degraded or unavailable states.

Required actions:

- import external GGUF;
- refresh catalog and installed state;
- download and cancel;
- explicitly install;
- select for the application/use case;
- verify integrity;
- open model details;
- explicitly load or unload RAM residency when those controls are implemented;
- remove from storage after confirmation and ownership checks.

Acceptance criteria:

- digest and stable catalog identity are never inferred from filename or display name;
- installation does not imply selection or load;
- selection does not imply load;
- runtime release never deletes an installed artifact;
- orphaned, incompatible, unavailable, failed-verification and ownership-mismatch states are explicit;
- multiple models survive restart and reconciliation;
- active-model protection and metadata cleanup are deterministic;
- real download/install and recovery paths receive physical-device evidence.

The durable inventory contract is documented in [`harness-model-inventory-state.md`](harness-model-inventory-state.md).

## Diagnostics

Diagnostics contains source-backed sections for:

- Runs;
- Health;
- Resources;
- Benchmarks;
- Logs;
- Validation.

Global requirements:

- section changes and refresh are observational;
- explicit actions run off the main thread;
- each source has loading, empty, unavailable, populated and error states;
- request correlation remains privacy-safe;
- data is bounded and ordered deterministically;
- screen state and actions move behind the ViewModel/effect boundary.

### Runs and request timeline

- show real run status, load classification, timings, tokens and throughput;
- navigate to a dedicated chronological timeline;
- do not infer missing events;
- clear or restore detail state deterministically.

### Health

- show registered checks and persisted results;
- support explicit complete and targeted execution where capabilities exist;
- preserve `NOT_RUN`, worst-status aggregation and typed details;
- never execute checks during navigation or refresh.

### Resources

- capture memory and thermal data only through an explicit action or defined validation flow;
- retain bounded newest-first history;
- show unavailable measurements as gaps rather than zero;
- provide accessible summaries before graphical trends.

### Benchmarks

- isolate keys by application, use case, model digest and cold/warm load kind;
- distinguish active regression baseline from retained immutable captures;
- require matching post-baseline samples before evaluation;
- never recapture or replace a baseline during refresh.

### Logs

- filter only the mapped privacy-safe representation;
- support severity, component, event, request and allowlisted-field search;
- copy only privacy-safe output;
- link correlated entries to request timelines.

### Validation

- reuse the shared runtime and production model-store/backend contracts;
- produce a copyable/shareable privacy-safe report;
- distinguish emulator, host and physical-device evidence.

## Settings and developer tools

Settings must provide:

- local-inference and telemetry privacy disclosures;
- real storage summary without private paths;
- build, runtime and backend metadata when available;
- theme selection and persistence;
- developer-tool links to health, logs, validation and relevant diagnostics;
- confirmed storage cleanup actions with active-model protection.

Settings is not a parallel source of model or runtime state; it consumes the same shared state as Overview, Models and Diagnostics.

## State and architecture acceptance

- renderable state is immutable and ViewModel-owned;
- reducers remain pure and Android-independent;
- runtime, storage, clipboard, document picker and window work remain effects;
- migrated screens have no duplicate Activity-owned state;
- `MainActivity` becomes a composition, Activity Result and lifecycle root;
- prompts and output never enter saved state or persistent stores;
- callback races cannot overwrite terminal state;
- opening a screen never starts domain work.

## Design-system acceptance

- colors, typography, shapes, spacing and reusable components use `ui:design-system`;
- repeated patterns are extracted only when they represent a real shared concept;
- touch targets are at least 48 dp;
- text and status indicators meet applicable WCAG AA contrast;
- status is not conveyed by color alone;
- light, dark and system modes render real states consistently;
- illustrative mockup content is never shown as runtime data.

## Test and evidence matrix

Required automated evidence:

- pure reducer and presentation tests;
- ViewModel/effect orchestration tests with deterministic fakes;
- Compose semantics for loading, empty, unavailable, warning, error and success states;
- top-level and detail navigation, Back and restoration tests;
- compact, expanded, landscape and large-font coverage;
- screenshot regression for stable representative states;
- accessibility checks for labels, focus order, touch targets and contrast;
- Lint, compilation and packaging gates.

Required representative physical-device evidence:

- import and remote download/install;
- integrity verification;
- load, generate, stream and cancel;
- model recovery and protected removal;
- repeated memory lifecycle;
- real resource, benchmark and validation presentation;
- privacy-safe report capture.

## Completion boundary

The UX/UI workstream is complete only when:

- all five primary surfaces use shared source-backed state;
- `MainActivity` no longer owns duplicate renderable domain state;
- detail navigation and restoration are deterministic;
- explicit RAM residency controls are connected when the runtime policy is available;
- the automated state/accessibility/responsive matrix is green;
- representative physical-device evidence is recorded;
- no production-readiness claim relies only on emulator or host evidence.
