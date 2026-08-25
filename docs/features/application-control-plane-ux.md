# Applications control-plane UX

Status: target
Document type: feature-specification
Owner: apps/local-llm-phone-test
Canonical scope: feature.application-control-plane-ux
Read when: implementing or changing the Harness UI for consumer applications, assigned use cases or inference presets
Last reviewed: 2026-08-25

## Purpose

Define the durable Android experience for answering:

> Which applications use Harness, for which use cases, and which inference configuration will each application/use-case pair actually use?

The UI translates the host control-plane domain into a task-first product model. Room rows, Binder transactions, bindings and revisions stay out of the primary information architecture unless they create troubleshooting value.

Governing owners: [`../../design/ux-contract.json`](../../design/ux-contract.json), [`../../design/brand-kit.json`](../../design/brand-kit.json), [`../harness-ux-ui-implementation-plan.md`](../harness-ux-ui-implementation-plan.md), `ui/design-system`. Temporary sequencing belongs in [`../workstreams/application-control-plane-ux.md`](../workstreams/application-control-plane-ux.md).

## User outcome and constraints

Primary user: Android/AI engineer using Harness as the shared local runtime for one or more consumer apps.

Successful task model:

```text
Application
  -> Assigned use case
  -> Default preset
  -> Effective model/configuration policy
```

The user can inspect, choose/customize and verify configuration without interpreting binding IDs, preset exposures, Binder protocol or Room entities.

Non-goals:

- no replacement for Playground experimentation;
- no raw Binder/Room control UI;
- no second control-plane store;
- no implicit model load/download/inference on navigation;
- no prompt/generated-output persistence;
- no in-place mutation of suggested presets;
- no usability/compatibility claim without source-backed evidence.

## Terminology

| Internal concept | Primary UI term | Raw term location |
| --- | --- | --- |
| registered application | Application | Technical details |
| `ApplicationUseCaseBinding` | Assigned use case / Assignment | Technical details |
| preset exposure | Available preset | Technical details if useful |
| suggested/custom preset | Suggested / Custom preset | Normal UI |
| default binding/preset state | Default configuration / Default preset | Normal UI |
| activation | Active / In use when source-backed | Contextual state |
| IDs/revisions/signer | Technical details | Expert disclosure |

`Binding` is not a primary navigation label.

## Information architecture

Target compact primary navigation:

```text
Overview | Playground | Apps | Performance | Models
```

Diagnostics remains available through Settings/Developer tools and contextual deep links. Expanded layouts use the existing navigation rail.

Drill-down:

```text
Applications
  -> Application detail
      -> Assigned use case
          -> Preset detail
              -> Advanced settings
              -> Technical details
          -> Create custom preset
      -> Assign use case (only if supported)
```

A separate top-level Bindings screen is not part of this design.

## Critical journeys

### Consumer configuration

```text
Applications
 -> application
 -> assigned use case
 -> current default preset
 -> existing preset OR create custom preset
 -> review
 -> save/set default
 -> acknowledgement
 -> canonical re-read
 -> verified effective state
```

Success requires persisted source state, not optimistic UI-only state.

### Recovery

```text
unavailable/incompatible/stale state
 -> explain problem
 -> smallest valid recovery action
 -> canonical re-read
 -> recovered state or bounded failure
```

Valid recovery examples: `View compatible models`, `Reload changes`, `Retry`, `Re-enable assignment`, `Review application identity` when supported by the source condition.

## View contract

### V1 — Applications

Goal: identify apps using Harness and their high-level state.

Header: `Applications` + `Apps using the Harness shared runtime`.

Each row shows, when source-backed:

- icon/monogram, display name, package;
- authorization/availability state;
- activity recency such as `Active now`;
- assigned-use-case count;
- navigation affordance.

Do not show application ID, signer hash or revision here. Do not show `Add application` unless a real user-driven registration capability exists.

Empty copy: `No applications connected` plus an explanation that authorized shared-runtime apps appear here.

### V2 — Application detail

Goal: answer **what does this app use Harness for?**

Header block: app name, package, semantic status and recency.

Primary content: Assigned use cases. Each row shows use-case name, concise purpose, default preset, optional model-policy summary, state and navigation affordance.

Contextual `Connection` information may include first seen, last seen and assignment count.

`Assign use case` appears only with a real host mutation capability. `Technical details` contains application identity/signer/revision.

### V3 — Assigned use case

Goal: answer **which configuration will this app use for this use case?**

Keep application identity as subtitle/context. Show source-backed state such as Active, Disabled, Setup required, Incompatible, Stale or Unavailable.

`Default configuration` is the dominant summary and shows:

- preset name;
- Suggested/Custom origin;
- Default state;
- model-selection policy;
- context size and concise purpose when available.

`Available presets` lists only published/exposed presets. Never infer `faster`, `quality` or similar claims from a name alone.

Primary contextual action: open/select preset. Secondary: `Create preset`. Enable/disable/unassign appear only if supported; destructive unassignment requires confirmation.

Setting default is one explicit action followed by canonical refresh.

### V4 — Preset detail

Goal: explain what the preset represents and what can safely change.

Summary-first information:

- preset name, Suggested/Custom, Default;
- source-backed description;
- model-selection policy;
- context size, max output tokens, thinking mode;
- runtime/warm policy summary where available.

Suggested presets are immutable in normal UI. Actions: `Use as default` when applicable and `Duplicate & customize`.

Custom presets may expose Edit, Set default, Duplicate and Delete according to domain support. Referenced-preset deletion is prevented or explains required reassignment.

`Advanced settings` and `Technical details` are progressive disclosures.

### V5 — Advanced settings

Goal: expose expert inference parameters without making them prerequisites for normal use.

Sections, only for fields supported by the canonical inference contract:

- Sampling: temperature, top P, top K, min P;
- Penalties: repeat penalty, repeat last N, presence penalty;
- Determinism/execution: seed policy/value and other owned policy fields.

Use numeric fields when precision matters; sliders may accompany bounded values only when accessibility and precision remain intact. Invalid values/combinations are rejected inline using canonical validation.

`Reset to recommended` exists only when a real base/recommended source is defined.

### V6 — Create preset

Goal: create a usable custom preset without configuring everything from scratch.

Default path starts from a real published preset, preferably the current default suggested preset. `Custom from scratch` is expert-only. Do not invent Fast/Quality templates unless the host publishes them.

Hierarchy:

```text
Essential: name + base preset + model policy
Contextual: context/max output/thinking/runtime summary
Advanced: sampling/penalties/determinism
```

Primary action: `Save preset`.

Save contract:

```text
acknowledge -> revision-aware persist -> canonical re-read -> success/failure
```

No optimistic-only success.

### V7 — Preset saved

Goal: confirm a durable result and expose the next decision.

Show success icon/semantics, `Preset saved`, preset name and concise confirmation.

Next actions: `Set as default` when not already default, `View preset`, `Done`/Back.

Setting the new preset as default remains explicit, not an implicit side effect.

### V8 — Technical details

Goal: support debugging/evidence without polluting primary workflows.

May show:

- application ID, package, signer SHA-256, registration/first-seen state;
- use-case ID/revision;
- assignment/binding ID/revision/enabled state;
- default preset ID/revision/provenance.

Rules: IDs remain secondary to human labels; long values are copyable; missing data is `Unavailable`; private paths, document URIs, signed URLs, prompts and generated output are prohibited.

### V9 — Assign use case

Conditional on a real host mutation capability.

Step 1 lists assignable use cases not already assigned, with purpose and compatibility/setup state.

Step 2 reviews application, use case, safe default suggested preset, model-selection policy and setup blockers.

Primary action: `Assign to <application>`.

Normal assignment uses a sensible default; advanced customization must not block a valid default path. Prevent invalid assignments where deterministically possible, otherwise expose `Setup required` plus recovery.

## Hierarchy and actions

Across all views:

```text
essential: app/use-case/preset identity + current state
contextual: effective model/config summary + activity
advanced: inference/runtime parameters
expert: IDs + revisions + signer + diagnostics
```

Use existing Harness primary/secondary/destructive semantics. Do not place multiple unrelated filled primary actions on one surface. Prefer spacing/proximity before additional containers.

## State and recovery matrix

| State | Required UX |
| --- | --- |
| loading | bounded progress/placeholder; avoid flicker |
| empty apps | explain how apps appear |
| empty assignments | explain no assignment; offer Assign only if supported |
| populated | normal hierarchy |
| disabled/unavailable | state label + valid recovery when available |
| identity changed | warning + identity review/re-authorization path |
| no presets | explain gap + create/recovery path |
| setup required/incompatible | user-language reason + smallest recovery action |
| saving | prevent duplicate submission; preserve form state |
| success | acknowledge + canonical re-read + next action |
| failure | preserve edits + actionable error + Retry where valid |
| stale revision | never overwrite; `Configuration changed elsewhere` + `Reload changes` |
| permission denied | explain authorization boundary; no hidden retry loop |
| destructive action | name exactly what is removed/retained |

Stale-revision behavior is strict: no silent retry with a newer revision; preserve safe unsaved input in process memory; reload canonical state; require re-confirmation if the target/effective state changed.

Error copy answers what failed, why when known, whether anything changed and what to do next. Technical codes are optional under Technical details, not the only explanation.

## Adaptive behavior

Compact portrait: single-pane drill-down, detail routes hide top-level nav, deterministic Back.

Compact landscape: same priority with more horizontal space; do not shrink touch targets or primary text.

Medium/expanded: use master-detail where useful, preferably:

```text
Applications list | Selected application detail
```

A deeper use-case/preset detail replaces the detail pane or uses another pane only when width supports it without crowding. Do not stretch phone cards to fill space.

Selection, non-sensitive unsaved form state and scroll position survive ordinary window changes according to existing ViewModel/saved-state policy.

## Accessibility

Target: Android accessibility with WCAG 2.2 AA-equivalent contrast/semantics.

Required:

- minimum 48dp targets;
- TalkBack labels include object + state;
- status is never color-only;
- logical focus follows task order;
- dynamic text does not clip primary labels/actions;
- truncated IDs expose full accessible value/copy action;
- toggles announce state/consequence;
- validation errors are associated with fields and announced;
- material success/error changes are announced;
- reduced-motion preserves all meaning.

## Visual/design-system contract

Maintain the existing Harness visual language:

- `HarnessTheme`/Material semantic surfaces;
- Harness purple for primary emphasis;
- semantic success/warning/error tones;
- existing typography, spacing, shapes, icons and light/dark/system themes;
- list rows lighter than grouped configuration/preset cards;
- no gradients, glow, glass, particles, decorative 3D graphs or celebratory animation.

Use semantic tokens, never reference mockup colors directly. Functional state/evidence precedes decoration.

Reuse existing `HarnessCard`, `HarnessStatusBadge`, primary/secondary actions, detail top bar and shell patterns. Add a shared component only for a repeated semantic role that existing primitives cannot express. App-specific composition stays in `apps/local-llm-phone-test`; reusable primitives stay in `ui/design-system`.

## Motion

Optional and restrained: navigation continuity, pressed/selection feedback, expand/collapse, saving progress and brief status transition only. Do not animate rows merely because data refreshed. Reduced-motion may use immediate state/simple fade.

## Privacy, navigation and ownership

Never display/persist prompts, generated output, private filesystem paths, document URIs, signed model/download URLs or arbitrary backend exceptions.

Opening Applications/app/use-case/preset is observational only: no model activation/load/download/inference.

Target route concepts:

- `applications`;
- application detail;
- application/use-case detail;
- preset detail;
- new preset;
- technical-detail child route where needed.

Route arguments use bounded opaque IDs, not package/signer/domain serialization. Process recreation re-reads canonical source state.

Compose renders immutable ViewModel state. Screens never access Room/control-plane repositories directly. Explicit mutations run through a neutral gateway, are revision-aware and re-read the canonical host state before success is final.

## Acceptance

Task/IA:

- Apps is discoverable from compact/expanded primary shell;
- app -> assigned use case -> preset drill-down is understandable without raw bindings;
- current Default and Suggested/Custom origin are obvious;
- technical IDs/revisions are available but progressively disclosed.

Mutation/state:

- set-default and custom-preset save are explicit, guarded, persisted and re-read;
- suggested presets are never edited in place;
- stale revisions cannot overwrite newer state;
- loading, empty, unavailable, setup/incompatible, saving, success, failure and stale states are deterministic.

Platform/design:

- compact portrait/landscape and medium/expanded preserve priority;
- TalkBack, large text, focus, 48dp targets and non-color-only status pass;
- existing Harness tokens/components are reused;
- no illustrative mockup values enter production state.

Evidence:

- reducer/presentation tests;
- ViewModel/effect read/save/conflict/recovery tests;
- navigation/Back/restoration tests;
- Compose semantics + accessibility/adaptive coverage;
- persistence/restart integration for default/custom preset state;
- representative two-APK device evidence for one consumer when shared-runtime release/effective-configuration claims require it.

A screenshot alone is not completion evidence.
