# Harness Android UX/UI target specification

Status: active
Document type: target-specification
Owner: apps/local-llm-phone-test
Canonical scope: target.phone-ux
Read when: changing connected-phone screen behavior, interaction patterns or UX acceptance criteria
Last reviewed: 2026-08-25

## Purpose

This document defines the top-level product behavior and acceptance criteria for the connected Android Harness application. Current implementation status belongs in [`harness-ux-ui-implementation-progress.md`](harness-ux-ui-implementation-progress.md) and [`current-state.md`](current-state.md).

The primary application is `apps/local-llm-phone-test`, branded as **Harness — Local AI Console**. It is the connected product surface for model management, local inference, evaluation, shared-runtime consumer configuration, diagnostics and physical-device validation.

The implementation must:

- keep inference and GGUF data on-device;
- preserve shared-runtime, host control-plane, model-store, cancellation, cleanup and integrity boundaries;
- treat Binder/AIDL as transport/integration ownership rather than exposing it as normal product UI;
- use real source-backed state and never substitute illustrative mockup values;
- support compact phones, larger devices, portrait and landscape;
- support dynamic text, TalkBack, deterministic focus and low-motion operation;
- keep prompt and generated output outside normal telemetry and persistence;
- model user tasks before internal architecture and progressively disclose expert/debug complexity.

The durable application architecture is documented in [`features/phone-app-architecture.md`](features/phone-app-architecture.md). The detailed shared-runtime consumer configuration experience is specified in [`features/application-control-plane-ux.md`](features/application-control-plane-ux.md).

## Product structure

Target primary destinations:

1. Overview
2. Playground
3. Applications
4. Performance
5. Models

`Settings` remains accessible from the application chrome. `Diagnostics` remains a first-class capability but moves out of the compact primary destination set into Settings/Developer tools and contextual deep links, because it is an expert/evidence surface rather than the normal consumer-configuration task.

Compact widths use bottom navigation. Medium and expanded widths use a navigation rail. Detail destinations hide top-level navigation and provide deterministic Back behavior.

The primary mental models are:

```text
Overview       -> What is Harness doing now?
Playground     -> Can this model/configuration perform my task?
Applications   -> Which apps/use cases use Harness and with what configuration?
Performance    -> Which model/configuration is best supported by evidence?
Models         -> Which local model artifacts are installed/available/selected?
Diagnostics    -> Why did something happen and what evidence supports it?
Settings       -> How is Harness configured as a product/device tool?
```

## Global presentation rules

- `Harness` is the product brand; `Local AI Console` is the descriptor.
- GGUF is the supported local model format for the current runtime.
- Model names, memory, temperatures, throughput, latency, application state and preset configuration must be real values or explicit unavailable/not-run states.
- Model installation, Harness-internal selection, shared-runtime assignment/activation and RAM residency must be visually and behaviorally distinct.
- Health, benchmark, resource and validation actions are explicit; navigation and refresh do not start work implicitly.
- Opening Applications, an application, an assigned use case or a preset is observational; it must not activate/load/download/infer.
- Destructive storage/control-plane actions require confirmation and must explain what is retained or removed.
- No UI surface exposes private filesystem paths, document URIs, download URLs, signed URLs, prompts, generated output or arbitrary backend exception messages.
- Internal concepts such as binding IDs, preset exposures, Binder protocol revisions and raw store identities are progressively disclosed under Technical details when they create debugging value.

## Shared shell and navigation

The application shell must provide:

- stable top-level route identity;
- compact bottom navigation and expanded navigation rail;
- detail-aware top bars and deterministic Back behavior;
- state restoration that does not persist sensitive content;
- system-bar and display-cutout handling;
- no runtime, download, health, resource, benchmark or control-plane activation side effects from route changes;
- process recreation behavior covered by deterministic tests.

Detail routes include:

- application details;
- assigned-use-case details;
- preset details/editor/technical details;
- model details;
- request timeline;
- Settings privacy, storage, build and developer tools;
- physical validation.

Route arguments must use bounded opaque identifiers rather than serialized domain objects, package/signature identities or private paths.

## Overview

Overview must summarize current source-backed state without becoming a second control plane.

Required content:

- selected Harness-internal model and installed/selected/loaded distinction;
- runtime state and active work;
- latest real Playground result metrics when available;
- resource-pressure summary when captured;
- latest run or diagnostic status;
- direct navigation to the relevant detailed surface.

Overview may surface a compact consumer/runtime warning when an application assignment requires attention, but application/use-case configuration remains owned by Applications.

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
- versioned presets and custom sampling controls for Harness-internal experimentation;
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

## Applications

Applications is the primary control-plane product surface for shared-runtime consumers.

The task model is:

```text
Application
  -> Assigned use case
  -> Default preset
  -> Effective model/configuration policy
```

The UI must not require users to understand `ApplicationUseCaseBinding`, Room entities or Binder transactions to complete this task.

Detailed behavior is owned by [`features/application-control-plane-ux.md`](features/application-control-plane-ux.md).

### Applications list

Required behavior:

- list source-backed registered/recognized consumer applications;
- show application display identity, package, authorization/availability state, assignment count and activity recency when available;
- provide explicit empty/loading/error states;
- open Application detail without starting runtime work.

### Application detail

Required behavior:

- make assigned use cases the primary content;
- show current default preset per use case;
- show application authorization/availability and connection metadata contextually;
- progressively disclose package/signature/revision identity under Technical details;
- expose `Assign use case` only when a real host mutation capability exists.

### Assigned use case

Required behavior:

- retain the application identity as context;
- make current default configuration obvious;
- list available/published presets;
- distinguish Suggested vs Custom and Default state;
- summarize model-selection/context/runtime policy without exposing low-level flags by default;
- expose setup/incompatibility blockers with actionable recovery;
- support explicit set-default and custom-preset creation where canonical host contracts permit them.

### Preset detail and editor

Required behavior:

- use summary -> Advanced -> Technical disclosure;
- never edit suggested presets in place; use `Duplicate & customize`;
- create custom presets from a sensible real published base when available;
- use exact numeric controls where inference values require precision;
- validate through canonical inference/control-plane rules rather than duplicating policy in Compose;
- persist through revision-aware host mutations and re-read canonical state before presenting success.

### Technical details

May expose application/use-case/binding/preset IDs, signer and revisions for debugging. These values remain subordinate to the human-readable task model and may never expose prompts/output/private paths/URIs/signed URLs.

### Applications acceptance

- Applications is reachable from compact bottom navigation and expanded rail;
- user can navigate Application -> Assigned use case -> Preset deterministically;
- current default and Suggested/Custom origin are visually and semantically obvious;
- stale revision fails closed and cannot overwrite newer state;
- mutation success is verified by canonical source re-read;
- loading, empty, disabled, unavailable, setup-required, incompatible, saving, success, failure and stale-revision states are covered;
- compact/landscape/medium/expanded and large-font/TalkBack behavior preserve priority;
- source-backed effective consumer configuration is proven E2E after the accepted control-plane Binder/cutover path is integrated.

## Performance

Performance owns model/configuration evaluation and evidence-backed comparison.

Required product journey:

```text
Run -> Datasets -> History -> Compare
```

Required behavior:

- allow the user to run a selected compatible evaluation configuration;
- present dataset/sample identity and configuration provenance;
- keep History durable and inspectable;
- compare only compatible evidence;
- fail closed rather than inventing a ranking when latency/throughput/memory/quality evidence is incomplete or incompatible;
- link model/configuration decisions back to Models/Playground/Applications only when the target identity is explicit.

Performance does not become an application-binding editor; Applications owns deployment/assignment configuration.

## Models

Models presents one reconciled view of:

- curated catalog releases;
- installed catalog metadata;
- external imported GGUF models where still supported by the current product contract;
- Harness-internal active selection;
- runtime-loaded ownership;
- degraded or unavailable states;
- assignment/effective-use context only when source-backed and useful to prevent destructive actions.

Required actions:

- import external GGUF where enabled;
- refresh catalog and installed state;
- download and cancel;
- explicitly install;
- select for Harness-internal Playground/device validation;
- verify integrity;
- open model details;
- explicitly load or unload RAM residency when those controls are implemented;
- remove from storage after confirmation and ownership checks.

Acceptance criteria:

- digest and stable catalog identity are never inferred from filename or display name;
- installation does not imply Harness selection, application assignment or load;
- selection does not imply load;
- runtime release never deletes an installed artifact;
- orphaned, incompatible, unavailable, failed-verification and ownership-mismatch states are explicit;
- multiple models survive restart and reconciliation;
- active model/activation protection and metadata cleanup are deterministic;
- real download/install and recovery paths receive physical-device evidence.

The durable inventory contract is documented in [`harness-model-inventory-state.md`](harness-model-inventory-state.md).

## Diagnostics

Diagnostics is an expert/evidence capability reached from Settings/Developer tools and contextual deep links.

It contains source-backed sections for:

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
- screen state and actions remain behind the ViewModel/effect boundary;
- application/use-case/binding/preset identities may be shown when telemetry has real attribution, but Diagnostics does not mutate control-plane state.

### Runs and request timeline

- show real run status, application/use-case attribution where available, load classification, timings, tokens and throughput;
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

- isolate keys by application, use case, model digest and cold/warm load kind where source identity exists;
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
- developer-tool links to Diagnostics, health, logs, validation and relevant evidence surfaces;
- confirmed storage cleanup actions with active-model/activation protection.

Settings is not a parallel source of application, model or runtime state; it consumes the same canonical state as the rest of the product.

## State and architecture acceptance

- renderable state is immutable and ViewModel-owned;
- reducers remain pure and Android-independent;
- runtime, control-plane mutation, storage, clipboard, document picker and window work remain effects;
- migrated screens have no duplicate Activity-owned state;
- `MainActivity` remains a composition, Activity Result and lifecycle root;
- prompts and output never enter saved state or persistent stores;
- callback races cannot overwrite terminal state;
- control-plane revision races cannot overwrite newer state;
- opening a screen never starts domain work.

## Progressive-disclosure acceptance

Across the product, complexity follows:

```text
essential
  -> contextual
  -> advanced
  -> expert/diagnostics
```

Applications specifically maps this to:

```text
app/use-case/default preset
  -> effective model/config summary
  -> inference/runtime settings
  -> IDs/revisions/signer/debug evidence
```

Normal tasks must not require expert information merely because the implementation uses it internally.

## Design-system acceptance

- colors, typography, shapes, spacing and reusable components use `ui:design-system`;
- repeated patterns are extracted only when they represent a real shared concept;
- touch targets are at least 48 dp;
- text and status indicators meet applicable WCAG AA contrast;
- status is not conveyed by color alone;
- light, dark and system modes render real states consistently;
- illustrative mockup content is never shown as runtime data;
- Applications uses the same Harness surfaces, purple primary emphasis and semantic status language as the existing app; no separate admin-console visual system is introduced.

## Adaptive behavior

Compact portrait uses single-pane drill-down.

Compact landscape preserves priority without shrinking touch targets.

Medium/expanded layouts use contextual master-detail where it reduces navigation, particularly:

```text
Applications list | Selected application detail
```

Do not simply stretch phone cards across expanded windows. State relevant to the user's current task must survive orientation/window changes according to the existing non-sensitive state policy.

## Accessibility

Required across all primary/detail surfaces:

- Android platform semantics and WCAG 2.2 AA-equivalent contrast;
- deterministic TalkBack/focus order;
- full-state labels that do not depend on color;
- dynamic text without clipping primary actions/state;
- 48 dp minimum interaction targets;
- accessible full values/copy actions for truncated technical IDs;
- field-associated validation errors;
- material state changes announced where needed;
- reduced-motion behavior with no loss of meaning.

## Motion and graphics

Motion remains restrained and purposeful: feedback, continuity, state transition, progress or attention only.

Applications does not use gradients, glow, glass, particles, decorative 3D relationship diagrams or celebratory animations. A small functional app -> use case -> preset orientation cue is acceptable only when it improves comprehension and is not required to operate the UI.

## Test and evidence matrix

Required automated evidence:

- pure reducer/presentation tests;
- ViewModel/effect orchestration tests with deterministic fakes;
- control-plane read/mutation/revision-conflict integration tests for Applications;
- Compose semantics for loading, empty, unavailable, warning, error, saving and success states;
- top-level/detail navigation, Back and restoration tests;
- compact, expanded, landscape and large-font coverage;
- scoped screenshot regression for stable representative states where useful;
- accessibility checks for labels, focus order, touch targets and contrast;
- Lint, compilation and packaging gates.

Required representative physical-device evidence:

- import and remote download/install where current product scope enables them;
- integrity verification;
- load, generate, stream and cancel;
- model recovery and protected removal;
- repeated memory lifecycle;
- real resource, benchmark and validation presentation;
- privacy-safe report capture;
- representative two-APK shared-runtime flow proving that a persisted application/use-case/default-preset assignment is honored by the accepted consumer control-plane path after restart.

## Completion boundary

Repository-side UX/UI is complete when:

- the five primary surfaces Overview, Playground, Applications, Performance and Models use shared source-backed state;
- Diagnostics/Settings retain deterministic expert/support access without competing with primary navigation;
- Applications implements the durable task-first configuration contract;
- `MainActivity` does not own duplicate renderable domain/control-plane state;
- detail navigation and restoration are deterministic;
- explicit RAM residency controls are connected when runtime policy is available;
- the automated state/accessibility/responsive matrix is green;
- no production-readiness claim relies only on emulator or host evidence.

The Applications feature additionally requires accepted end-to-end shared-runtime control-plane wiring before claiming that UI-selected assignments/presets are effective for a real external consumer. Missing physical/effective-consumer evidence remains an explicit gate rather than being inferred from screenshots or repository-side tests.
