# Harness Android UX/UI target specification

Status: active
Document type: target-specification
Owner: apps/local-llm-phone-test
Canonical scope: target.phone-ux
Read when: changing connected-phone screen behavior, interaction patterns or UX acceptance criteria
Last reviewed: 2026-08-25

## Purpose

Define top-level product behavior and acceptance for the connected Android Harness application. Current implementation state belongs in [`harness-ux-ui-implementation-progress.md`](harness-ux-ui-implementation-progress.md) and [`current-state.md`](current-state.md).

`apps/local-llm-phone-test`, branded **Harness — Local AI Console**, is the connected surface for local inference, model lifecycle, evaluation, shared-runtime consumer configuration, diagnostics and device validation.

The product must:

- keep inference/GGUF data on-device and prompt/output outside normal persistence;
- preserve shared-runtime, control-plane, model-store and lifecycle ownership;
- expose user tasks rather than Binder/Room/internal architecture by default;
- use source-backed values or explicit unavailable/not-run states;
- support compact/expanded, portrait/landscape, dynamic text, TalkBack and reduced motion;
- never start runtime/domain work merely because a route opened or refreshed.

Architecture owner: [`features/phone-app-architecture.md`](features/phone-app-architecture.md). Applications detail owner: [`features/application-control-plane-ux.md`](features/application-control-plane-ux.md).

## Product structure

Target primary destinations:

1. Overview
2. Playground
3. Applications
4. Performance
5. Models

Settings remains in application chrome. Diagnostics remains a first-class expert/evidence capability reached through Settings/Developer tools and contextual deep links rather than competing for compact primary navigation.

Mental model:

```text
Overview      -> What is Harness doing now?
Playground    -> Can this model/configuration perform my task?
Applications  -> Which apps/use cases use Harness and with what configuration?
Performance   -> Which model/configuration is best supported by evidence?
Models        -> Which local model artifacts are available/installed/selected?
Diagnostics   -> Why did something happen and what evidence supports it?
Settings      -> How is Harness configured as a product/device tool?
```

Compact widths use bottom navigation; medium/expanded widths use the existing rail. Detail destinations hide top-level nav and provide deterministic Back behavior.

## Global product rules

- `Harness` is the product brand; `Local AI Console` is the descriptor.
- GGUF/model identity is content/source-backed, never inferred from display text alone.
- Installation, Harness-internal selection, application assignment/activation and RAM residency are distinct states/actions.
- Health, resource, benchmark, validation and control-plane mutations are explicit effects.
- Opening Applications/app/use-case/preset is observational only.
- Destructive storage/control-plane actions name consequences and require confirmation when irreversible.
- Private paths, document URIs, signed URLs, prompts, generated output and arbitrary backend exceptions never enter normal UI evidence.
- Binding IDs, preset exposures, protocol revisions and raw store identities belong under progressive Technical details.

## Shared shell

Required shell behavior:

- stable top-level destination identity;
- compact bottom navigation and expanded rail;
- detail-aware top bars and deterministic Back;
- non-sensitive state restoration across recreation/window changes;
- system-bar/cutout handling;
- bounded opaque route arguments;
- no side effects from navigation.

Detail routes include application, assigned-use-case and preset details, preset editing/technical detail, model details, request timeline, Settings detail and physical validation.

## Overview

Overview summarizes current source-backed state without becoming a second control plane.

Required:

- Harness-internal selected model and installed/selected/loaded distinction;
- runtime state/active work;
- latest real Playground metrics when available;
- resource-pressure summary when captured;
- latest run/diagnostic status;
- one clear next action/deep link when attention is needed.

Application-assignment warnings may link to Applications, but assignment configuration stays owned there.

## Playground

Playground performs real local generation through the shared runtime.

Required:

- process-memory-only prompt/output;
- explicit model requirement/preparation;
- Basic -> Advanced -> Expert configuration disclosure;
- real effective configuration;
- queued/preparing/prefill/generating/cancelling/terminal states;
- cooperative cancellation and bounded streaming;
- terminal metrics, privacy-safe failures and deterministic cleanup;
- navigation that does not implicitly cancel work.

It must not duplicate runtime validation/prompt policy. Real-GGUF physical evidence remains required for representative device claims.

## Applications

Applications owns shared-runtime consumer configuration.

Task model:

```text
Application
 -> Assigned use case
 -> Default preset
 -> Effective model/configuration policy
```

Detailed behavior, nine view contracts, states and acceptance are in [`features/application-control-plane-ux.md`](features/application-control-plane-ux.md).

Top-level requirements:

- list source-backed recognized applications and high-level authorization/availability state;
- drill Application -> Assigned use case -> Preset without exposing raw binding structures;
- make Default and Suggested/Custom semantics obvious;
- summarize effective model/context/runtime configuration before Advanced/Technical details;
- allow set-default/custom-preset/assignment mutation only where canonical host capabilities exist;
- never mutate suggested presets in place;
- make writes revision-aware and re-read canonical state before success is final;
- fail closed on stale revisions/incompatibility and provide actionable recovery;
- use application/use-case/preset technical IDs only as expert evidence;
- preserve source-backed behavior across restart/recreation.

Applications becomes the compact `Apps` destination; Diagnostics remains accessible via Settings/deep links.

End-to-end effectiveness requires the accepted HCP consumer-control-plane/cutover path plus representative two-APK evidence; host-only UI success is insufficient.

## Performance

Performance owns the evidence-backed evaluation journey:

```text
Run -> Datasets -> History -> Compare
```

Required:

- source-backed dataset/sample/config provenance;
- durable inspectable history;
- comparison only across compatible evidence;
- fail-closed ranking when latency/throughput/memory/quality evidence is incomplete;
- contextual links to Models/Playground/Applications only when target identity is explicit.

Performance does not edit application assignments.

## Models

Models owns the reconciled model inventory and lifecycle.

Required state:

- curated releases and installed metadata;
- imported/local artifacts where supported;
- Harness-internal selection;
- runtime residency/ownership;
- degraded/unavailable states;
- assignment/activation context only when source-backed and useful for safety.

Required explicit actions where supported:

- import/refresh/download/cancel/install;
- select for Harness-internal testing;
- verify integrity;
- open details;
- load/unload residency when product controls exist;
- confirmed safe removal.

Installation does not imply selection, assignment or load; selection does not imply load; runtime release never deletes storage. Active ownership/activation protects destructive actions.

Inventory contract: [`harness-model-inventory-state.md`](harness-model-inventory-state.md).

## Diagnostics

Diagnostics is an expert/evidence surface for Runs, Health, Resources, Benchmarks, Logs and Validation.

Global rules:

- navigation/refresh is observational;
- explicit work runs off main thread;
- sources cover loading/empty/unavailable/populated/error;
- correlation stays privacy-safe and deterministic;
- attributed application/use-case/binding/preset identity may be shown when real telemetry provides it;
- Diagnostics never mutates application control-plane state.

Runs/timelines show only recorded events/metrics. Health never runs implicitly. Resource gaps remain unavailable rather than zero. Benchmarks require compatible identities and explicit baseline capture. Logs filter/copy only allowlisted privacy-safe fields. Validation preserves environment identity and distinguishes host/emulator/physical evidence.

## Settings and developer tools

Settings provides:

- privacy/local-inference disclosures;
- storage summary without private paths;
- build/runtime/backend metadata;
- theme preference;
- Diagnostics/developer/validation links;
- confirmed cleanup actions with active model/activation protection.

Settings consumes canonical product state; it is not another state owner.

## State, hierarchy and recovery

Renderable state is immutable/ViewModel-owned; runtime/control-plane/storage/window work remains effectful; `MainActivity` remains composition/lifecycle/result root.

Progressive disclosure:

```text
essential -> contextual -> advanced -> expert/diagnostics
```

Applications maps this to:

```text
app/use-case/default preset
 -> effective configuration
 -> inference/runtime settings
 -> IDs/revisions/signer/debug evidence
```

Critical workflows cover applicable loading, empty, disabled, unavailable, warning, failure, success, cancellation and stale-revision states. User-facing failures explain what failed, why when known and the next valid recovery action. Revision races never silently overwrite newer state.

## Adaptive and accessibility

Compact portrait uses single-pane drill-down. Landscape preserves priority without shrinking touch targets. Medium/expanded uses master-detail when it reduces navigation, especially `Applications list | selected application`.

Required accessibility:

- Android semantics and WCAG 2.2 AA-equivalent contrast;
- 48dp minimum interaction targets;
- deterministic TalkBack/focus order;
- status not color-only;
- dynamic text without clipping primary actions;
- full accessible values/copy actions for truncated technical IDs;
- field-associated validation errors and meaningful status announcements;
- reduced-motion with no loss of meaning.

## Design system, motion and graphics

Use `ui:design-system` semantic colors, typography, spacing, shapes, surfaces and shared components. Applications uses the existing Harness purple primary emphasis, semantic status tones and light/dark/system themes; it does not introduce an admin-console visual system.

Reuse existing components before adding shared roles. Prefer spacing/proximity before more cards/borders.

Motion serves feedback, continuity, state transition, progress or attention only. No decorative gradients, glow, glass, particles, 3D relationship graphics or celebratory animation in Applications. Illustrative mockup values never enter runtime UI.

## Test and evidence matrix

Automated evidence:

- reducer/presentation and ViewModel/effect tests;
- control-plane read/write/revision-conflict integration tests for Applications;
- Compose semantics for representative normal/empty/error/saving/success states;
- primary/detail navigation, Back and recreation;
- compact/expanded/landscape/large-font coverage;
- accessibility/touch-target/contrast checks;
- scoped visual regression where stable/high-value;
- Lint, compile and packaging gates.

Representative physical evidence remains required for claims that depend on real device/runtime behavior: model lifecycle/generation/cancellation/memory, Diagnostics evidence, and the Applications two-APK flow proving a persisted default preset is honored by the accepted consumer control-plane path after restart.

## Completion boundary

Repository-side UX is complete when:

- Overview, Playground, Applications, Performance and Models use shared source-backed state;
- Settings/Diagnostics remain deterministic support surfaces without competing with primary task navigation;
- Applications satisfies its durable feature contract;
- no duplicate Activity-owned render/control-plane state exists;
- detail/back/restoration and state/recovery are deterministic;
- adaptive/accessibility automation is green;
- no privacy-boundary regression exists.

Applications may claim effective external-consumer configuration only after the accepted HCP wiring and representative E2E/device evidence exist. Emulator/host evidence cannot be promoted into that claim.
