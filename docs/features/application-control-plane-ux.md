# Applications control-plane UX

Status: target
Document type: feature-specification
Owner: apps/local-llm-phone-test
Canonical scope: feature.application-control-plane-ux
Read when: implementing or changing the Harness UI for consumer applications, assigned use cases, bindings or inference presets
Last reviewed: 2026-08-25

## Purpose

This document defines the durable user experience for inspecting and configuring application-to-use-case assignments in the Harness Android application.

The feature exists to answer one user question clearly:

> Which applications are using Harness, for which use cases, and which inference configuration will each application/use-case pair actually use?

The UI must translate the existing host control-plane domain into a task-first product model. It must not expose Room tables, Binder transactions or internal revision objects as the primary information architecture.

The implementation is governed by [`../../design/ux-contract.json`](../../design/ux-contract.json), [`../../design/brand-kit.json`](../../design/brand-kit.json), [`../harness-ux-ui-implementation-plan.md`](../harness-ux-ui-implementation-plan.md) and the shared `ui/design-system`.

Implementation sequencing and dependency state belong in [`../workstreams/application-control-plane-ux.md`](../workstreams/application-control-plane-ux.md), not here.

## User outcome

Primary user:

- Android/AI engineer using Harness as the shared local runtime for one or more consumer applications.

Primary job:

- inspect an application;
- understand which Harness use cases are assigned to it;
- understand the effective default inference preset for each assignment;
- choose a different published preset or create a custom preset when required;
- verify that the saved assignment is active and traceable;
- drill into technical identity/revision information only when troubleshooting.

Successful outcome:

```text
application
  -> assigned use case
  -> default preset
  -> effective model/configuration policy
```

is understandable without requiring the user to interpret binding IDs, preset exposures, Binder protocol concepts or database rows.

## Non-goals

This feature does not:

- replace Playground model/configuration experimentation;
- expose raw Binder/AIDL protocol controls;
- expose Room entities as editable records;
- make application package/signature authorization implicit;
- silently activate, load or download a model merely because the user opens a screen;
- persist prompts or generated output;
- introduce a second control-plane store or app-local copy of host policy;
- automatically mutate suggested presets when the user edits configuration;
- claim that a model/preset is usable when source-backed compatibility or residency evidence says otherwise.

## Product terminology

Internal architecture remains available in diagnostics, but normal UI uses task language.

| Internal concept | Primary UI term | Where raw term may appear |
| --- | --- | --- |
| registered application | Application | Technical details |
| `ApplicationUseCaseBinding` | Assigned use case / Assignment | Technical details |
| use-case revision | Use case | Technical details |
| preset exposure | Available preset | Technical details only if needed |
| suggested preset | Suggested preset | Normal UI |
| custom preset | Custom preset | Normal UI |
| default binding/preset state | Default configuration / Default preset | Normal UI |
| activation | Active / In use when source-backed | Contextual status |
| application/binding/preset revision IDs | Technical details | Expert disclosure |

The word **binding** must not be a primary navigation label.

## Information architecture

### Top-level destination

`Applications` becomes the primary surface for shared-runtime consumer configuration.

Target compact navigation:

```text
Overview | Playground | Apps | Performance | Models
```

`Diagnostics` remains available through Settings/Developer tools and contextual deep links. This prevents expert/debug surfaces from competing with a primary product task.

Expanded layouts use the existing navigation rail with the same destination semantics.

### Drill-down hierarchy

```text
Applications
    |
    +-- Application detail
            |
            +-- Assigned use case
                    |
                    +-- Preset detail
                    |      |
                    |      +-- Advanced inference settings
                    |      +-- Technical details
                    |
                    +-- Create custom preset
                    +-- Assignment technical details
```

The hierarchy is intentionally application-first. A separate top-level Bindings screen is prohibited unless future evidence shows a distinct user job that requires it.

## Critical journey

### Consumer configuration

```text
Applications
  -> select application
  -> inspect assigned use case
  -> inspect current default preset
  -> select existing preset OR create custom preset
  -> review configuration
  -> save/set as default
  -> immediate acknowledgement
  -> refreshed source-backed effective state
  -> optional technical verification
```

The journey succeeds only when the final surface reflects the persisted control-plane state, not an optimistic UI-only mutation.

### Recovery journey

```text
open assignment
  -> detect unavailable/incompatible/stale state
  -> explain what is wrong
  -> offer the smallest valid recovery action
  -> re-read source-backed state
  -> show recovered state or bounded failure
```

Examples of recovery actions include `View compatible models`, `Reload changes`, `Retry`, `Re-enable assignment` or `Review application identity`, depending on the actual failure source.

## View 1 — Applications

### Goal

Answer: **Which applications currently use Harness and what is their high-level state?**

### Header

Title: `Applications`

Supporting copy: `Apps using the Harness shared runtime`

Optional compact summary, only when source-backed:

- application count;
- assigned use-case count.

Do not add decorative KPI cards when the same information can be communicated with one compact summary row.

### Application row

Required information:

1. application icon/monogram when no production icon is available;
2. display name;
3. package name as secondary text;
4. authorization/availability state;
5. activity recency when source-backed, e.g. `Active now` or `2 min ago`;
6. number of assigned use cases;
7. navigation affordance.

Example structure:

```text
[RG]  RedactGuard                         Active now
      io.github.daniele21.redactguard
      1 assigned use case                       >
```

### Information hierarchy

Primary:

- app identity;
- whether it can currently use Harness.

Secondary:

- package name;
- assignment count;
- recency.

Expert information excluded from this screen:

- application ID;
- signer SHA-256;
- application revision;
- raw authorization-policy details.

### Actions

Primary action: tap application to inspect it.

There is no `Add application` CTA unless the product gains a real user-driven registration flow. Discovery/registration policy must not be faked by the UI.

### States

Empty:

```text
No applications connected

Applications authorized to use the Harness shared runtime will appear here.
```

Loading uses geometry-matched placeholders only when loading lasts long enough to avoid flicker.

Error explains whether the source is unavailable and offers `Retry` when retry is meaningful.

## View 2 — Application detail

### Goal

Answer: **What does this application use Harness for?**

### Header block

Required:

- application name;
- package name;
- semantic status: `Authorized`, `Disabled`, `Unavailable`, `Identity changed` or another source-backed state;
- recency if available.

Status must combine icon/text/semantics; color alone is insufficient.

### Primary section — Assigned use cases

Each row shows:

- use-case display name;
- one-line purpose description;
- current default preset name;
- optional current model-selection summary when source-backed;
- state such as Active/Disabled/Setup required;
- navigation affordance.

Example:

```text
Document PII detection
Structured PII detection on local documents

Default preset
Balanced local PII                              >
```

### Secondary action

`Assign use case` appears only when the host exposes a valid mutable assignment capability. It must not be shown disabled as decoration when assignment is not supported in the current product boundary.

### Connection section

Contextual information:

- first seen;
- last seen;
- number of assigned use cases.

### Expert disclosure

`Technical details` opens application identity, signer and revision information.

The screen must not place signer hashes or binding IDs beside the primary task.

## View 3 — Assigned use case

### Goal

Answer: **Which configuration will this application use for this use case?**

The title uses the use-case display name, for example `Document PII detection`. The application identity is retained as subtitle/context.

### Status

Show one source-backed semantic state:

- Active;
- Disabled;
- Setup required;
- Incompatible;
- Stale/reload required;
- Unavailable.

### Default configuration section

The current default preset is visually dominant but not oversized.

Required summary:

- preset name;
- `Suggested` or `Custom` origin;
- `Default` state;
- model-selection policy summary;
- context size when source-backed;
- concise purpose copy.

Example:

```text
Balanced local PII                         DEFAULT
Recommended preset for general purpose

Model                     Context
Automatic selection       4,096 tokens              >
```

### Available presets

Show all presets published/exposed for this application/use-case pair.

Each row contains:

- preset name;
- Suggested/Custom semantic label;
- purpose such as `Recommended` or `Lower latency` when the source defines it;
- Default marker if applicable;
- navigation affordance.

Do not infer quality/latency claims from preset names alone.

### Actions

Primary contextual action: select/open a preset.

Secondary action: `Create preset`.

Assignment-level action: enable/disable only if the domain supports it and recovery semantics are defined.

Destructive/unassignment actions belong behind overflow or a lower-level section and require confirmation when they remove durable configuration.

### Default change

Setting another preset as default must be one explicit action and produce immediate acknowledgement followed by a source refresh.

Success copy example:

`Fast is now the default for RedactGuard · Document PII detection.`

Do not navigate away automatically if remaining on the assignment helps verification.

## View 4 — Preset detail

### Goal

Answer: **What configuration does this preset represent, and what can I safely change?**

### Summary-first layout

Header:

- preset name;
- Suggested/Custom origin;
- Default state where applicable;
- short source-backed description.

Summary section:

- model selection policy;
- context size;
- max output tokens;
- thinking mode;
- other immediately decision-relevant values supported by the contract.

Runtime section:

- warm retention/cache behavior when available;
- avoid raw internal cache flags in the normal summary.

### Suggested preset behavior

Suggested presets are immutable from the normal UI.

Primary action when non-default: `Use as default`.

Secondary action: `Duplicate & customize`.

If already default, the default state is shown and the main mutable action becomes `Duplicate & customize`.

Editing a suggested preset in place is prohibited because it would make the suggested identity non-reproducible.

### Custom preset behavior

Custom presets may expose `Edit`, `Set as default`, `Duplicate` and `Delete` according to domain support.

Delete requires confirmation if the preset is referenced; the UI must either prevent deletion or explain the reassignment requirement before destructive execution.

### Disclosure

`Advanced settings` contains detailed inference parameters.

`Technical details` contains IDs, revisions and provenance.

## View 5 — Advanced inference settings

### Goal

Allow an expert user to inspect or edit detailed inference parameters without making them prerequisites for normal use.

### Sections

Sampling:

- temperature;
- top P;
- top K;
- min P.

Penalties:

- repeat penalty;
- repeat last N;
- presence penalty.

Determinism/execution where supported:

- seed policy/value;
- other preset policy fields that are part of the canonical inference contract.

### Control behavior

Use numeric fields when exact values matter. A slider may accompany bounded high-frequency parameters only when it does not reduce precision or accessibility.

Each non-obvious parameter may expose contextual help using concise explanatory copy.

Invalid combinations must be rejected inline before save where deterministic validation is available.

### Reset

`Reset to recommended` is available only when the current preset has a well-defined base/recommended source. The reset action must preview or clearly describe what values will change if the change is broad.

## View 6 — Create preset

### Goal

Create a usable custom preset without forcing the user to configure every field from scratch.

### Entry model

The creation flow starts from an existing safe base whenever possible.

Preferred options:

- current default suggested preset;
- another published suggested preset;
- `Custom from scratch` only as an expert path.

Do not invent `Fast`, `Quality` or other starting templates unless such presets are actually published by the host.

### Form hierarchy

Essential:

- preset name;
- source/base preset;
- model selection policy summary.

Contextual:

- context size;
- max output tokens;
- thinking mode;
- runtime/warm policy summary.

Advanced:

- sampling/penalty/determinism parameters.

Expert:

- raw technical identity/provenance after creation, not while naming the preset.

### Primary action

`Save preset`

The action is disabled only when the form is invalid or a save is already in flight. Disabled state must be semantically explained when necessary.

### Save behavior

```text
Save
 -> immediate pressed/progress acknowledgement
 -> persist through owning control-plane capability
 -> re-read persisted preset
 -> success or actionable failure
```

Optimistic-only success is not allowed.

## View 7 — Preset saved

### Goal

Confirm a successful durable action and make the next decision obvious.

Use restrained success treatment; no decorative celebration animation is required.

Required:

- success icon with semantic label;
- `Preset saved`;
- preset name;
- one-line confirmation.

Next actions:

- `Set as default` when the new preset is not yet default;
- `View preset`;
- `Done`/Back.

If the product can safely set the preset as default inside the creation flow, that must remain an explicit user choice rather than an implicit side effect.

## View 8 — Technical details

### Goal

Support debugging, evidence and identity verification without polluting primary workflows.

### Application section

May include:

- application ID;
- package name;
- signer SHA-256 in copyable/truncated form;
- registration/first-seen timestamp;
- current application revision/state.

### Assignment section

May include:

- use-case ID;
- use-case revision;
- binding/assignment ID;
- binding revision;
- enabled state;
- default preset ID;
- preset revision;
- exposure/provenance identity when useful.

### Presentation rules

- long identifiers use monospace or code-friendly typography only where the existing design system supports it;
- copy actions are explicit and privacy-safe;
- private filesystem paths, document URIs, signed URLs, prompts and generated content are prohibited;
- IDs are secondary to human-readable labels;
- missing fields render `Unavailable`, never invented placeholder values.

## View 9 — Assign use case

This surface is conditional on a real host-side mutation capability.

### Goal

Assign an available use case to an application with a safe default configuration.

### Step 1 — Select use case

List only use cases the application is allowed to consume and which are not already assigned, unless reconfiguration semantics explicitly permit duplicates.

Each row shows:

- use-case name;
- concise purpose;
- compatibility/setup state.

### Step 2 — Review assignment

Required:

- selected application;
- use case;
- default suggested preset;
- model-selection policy summary;
- setup blockers.

Primary action: `Assign to <application>`.

The normal flow uses a sensible default preset. Advanced customization is optional and should not block assignment when the default is valid.

### Failure prevention

If there is no compatible installed model or another prerequisite is missing, prefer preventing an invalid assignment or clearly flagging `Setup required` with a recovery action rather than allowing a silent broken state.

## Information and action hierarchy

Across all views:

```text
essential
  -> application/use-case/preset identity and current state
contextual
  -> effective model/configuration summary and activity
advanced
  -> detailed inference/runtime parameters
expert/diagnostics
  -> raw IDs, revisions, signer, protocol/debug evidence
```

Primary actions use the existing Harness primary action treatment.

Secondary actions use existing secondary/text action semantics.

Destructive actions remain visually subordinate until intentionally invoked.

Do not place multiple unrelated filled primary buttons on one screen.

## State model

Every critical surface must define source-backed states.

| State | Required UX |
| --- | --- |
| loading | bounded progress/placeholder; no flicker for fast reads |
| empty applications | explanation of how applications appear |
| empty assignments | explain that no use cases are assigned; offer Assign only if supported |
| populated | normal task hierarchy |
| disabled | state label + valid re-enable/recovery when supported |
| unavailable | explain unavailable source/capability; no fake values |
| identity changed | warning + review/re-authorization path |
| no presets | explain configuration gap + create/recovery path |
| model/setup unavailable | `Setup required` + smallest recovery action |
| incompatible | explain incompatibility reason in user language; technical code optional below |
| saving | disable duplicate submission; preserve form state |
| save success | acknowledge + refresh persisted state + next action |
| save failure | preserve edits + explain failure + Retry where valid |
| stale revision | do not overwrite; offer `Reload changes` and preserve/reconcile local edits safely |
| permission denied | explain authorization boundary; no hidden retry loop |
| destructive confirmation | name exactly what will be removed and what remains |

## Stale-revision and concurrent-change behavior

Control-plane revisions are part of correctness.

If an update is rejected because the application, binding, use case or preset revision changed after the screen loaded:

1. do not silently retry with the new revision;
2. keep unsaved user input in process memory where safe;
3. show `Configuration changed elsewhere`;
4. offer `Reload changes`;
5. after reload, require the user to re-confirm any mutation whose target/effective state changed.

The UI must never silently overwrite a newer revision.

## Error language

Normal errors use user-recoverable language first.

Example:

```text
Setup required
Balanced local PII cannot run because no compatible installed model is available.

[ View compatible models ]
```

Technical codes such as `MODEL_UNAVAILABLE` may appear in Technical details, not as the only explanation.

Errors should answer:

- what failed;
- why, when known;
- whether anything changed;
- what the user can do next.

## Adaptive behavior

### Compact portrait

Use single-pane drill-down with deterministic Android Back behavior.

Context must remain visible in titles/subtitles:

```text
Applications
 -> RedactGuard
 -> Document PII detection
 -> Balanced local PII
```

Top-level bottom navigation is hidden on detail routes.

### Compact landscape

Preserve the same information priority. Dense technical rows may use more horizontal space, but do not reduce touch targets or compress text to unreadable sizes.

### Medium/expanded

Use a master-detail pattern when it reduces navigation without harming focus or state clarity.

Preferred pattern:

```text
Applications list | Selected application detail
```

A deeper use-case/preset detail may replace the detail pane or use a second contextual pane only if the available width supports it without creating three cramped columns.

Do not merely stretch phone cards across the screen.

### State continuity

Selection, unsaved non-sensitive form input and scroll position should survive ordinary recomposition/window changes according to existing ViewModel/saved-state policy. Prompts/generated output remain outside persistent saved state.

## Accessibility

Target: Android platform accessibility with WCAG 2.2 AA-equivalent contrast and semantics.

Required:

- minimum 48 dp touch targets;
- TalkBack labels include object + state, e.g. `RedactGuard, authorized, active now, one assigned use case`;
- status is never communicated only by purple/green/yellow/red;
- logical focus follows visual/task order;
- dynamic text can expand without clipping primary labels or actions;
- truncated hashes/IDs expose the full accessible value and copy action when appropriate;
- toggles announce state and consequence;
- validation errors are associated with the owning field and announced;
- success/error state changes are announced when they materially affect task completion;
- reduced-motion settings preserve all meaning without movement.

## Visual language

The feature must look native to the existing Harness application rather than like a separate admin console.

Use only semantic tokens/components from the current design owner:

- `MaterialTheme` + `HarnessTheme` surfaces;
- Harness purple for primary/action emphasis;
- semantic success/warning/error status tones;
- existing surface/background/elevated-surface hierarchy;
- existing typography, spacing, shapes and icon conventions;
- light, dark and system themes.

Dark-mode examples may use the current near-black background and elevated dark surfaces, but implementation must use semantic tokens rather than hard-coded reference colors.

Visual rules:

- prefer spacing/proximity before adding more containers;
- use cards only for meaningful grouped configuration or selectable preset summaries;
- ordinary application lists should remain visually lighter than preset/configuration cards;
- no gradients, glow, glass, particles, 3D diagrams or decorative animations in this workflow;
- functional status/evidence takes precedence over decoration;
- the UI remains understandable without icons.

## Component ownership

Reuse existing semantic components before introducing new ones.

Likely reusable owners:

- `HarnessCard` for grouped preset/configuration surfaces;
- `HarnessStatusBadge` for semantic state;
- `HarnessPrimaryButton` and `HarnessSecondaryButton` for action hierarchy;
- existing detail top bar and adaptive shell;
- existing list/divider/metric typography patterns.

New shared components are justified only if multiple Harness surfaces need the same semantic role. Candidate reusable roles may include:

- compact key/value technical row;
- app/use-case identity row;
- preset summary row;
- source-backed warning/recovery panel.

App-specific composition remains in `apps/local-llm-phone-test`; reusable visual primitives belong in `ui/design-system`.

## Motion

Motion is optional and restrained.

Allowed purposes:

- navigation continuity;
- pressed/selection feedback;
- inline expand/collapse continuity;
- progress while saving;
- brief status transition after successful mutation.

Do not animate list rows merely because data refreshed. Do not use bounce or celebratory motion for preset save. Reduced-motion may replace transition movement with immediate state or simple fade.

## Privacy and security

This UI may display control-plane metadata but must preserve repository privacy boundaries.

Never display or persist in this feature:

- prompt text;
- generated output;
- private filesystem paths;
- document URIs;
- signed model/download URLs;
- arbitrary backend exception messages.

Signer hashes and technical IDs are allowed only where they support application identity/debugging and must be intentionally disclosed under Technical details.

Opening Applications, an app, a use case or a preset is observational only. Navigation must not load models, activate a binding, start inference, download a model or run diagnostics.

## Navigation contract

Target route concepts:

- `applications`
- `application/{applicationKey}`
- `application/{applicationKey}/use-case/{useCaseKey}`
- `application/{applicationKey}/use-case/{useCaseKey}/preset/{presetKey}`
- `application/{applicationKey}/use-case/{useCaseKey}/preset/new`
- technical-detail child routes where a separate screen is preferable to inline disclosure.

Route arguments use bounded opaque identifiers, not package names, signer hashes or serialized domain objects.

Back behavior:

- preset -> assigned use case;
- assigned use case -> application;
- application -> Applications;
- process recreation restores route only when safe and re-reads canonical source state.

## Source-of-truth and state ownership

- Compose renders immutable ViewModel-owned state;
- screens do not access Room/control-plane repositories directly;
- read/mutation gateways expose neutral UI-facing contracts backed by the canonical host control plane;
- opening/refreshing is observational;
- mutations are explicit effects;
- the UI re-reads source state after mutation success;
- revision conflicts fail closed;
- no Activity-local duplicate binding/preset state is introduced;
- all visible values are source-backed or explicitly unavailable.

## Acceptance criteria

The feature is implementation-complete only when all of the following hold.

### Task/IA

- Applications is discoverable from the primary shell;
- user can identify every source-backed registered application state;
- user can drill from application -> assigned use case -> preset without understanding raw binding structures;
- current default preset is obvious at assignment level;
- suggested vs custom preset identity is obvious;
- technical IDs/revisions are available but progressively disclosed.

### Mutation behavior

- setting a preset default is explicit and persisted;
- creating a custom preset starts from a sensible existing base when available;
- suggested presets are not mutated in place;
- duplicate submissions are guarded;
- mutation success is followed by canonical re-read;
- stale revisions do not overwrite newer state;
- destructive actions are confirmed and recoverable/prevented where possible.

### State/recovery

- loading, empty, populated, disabled, unavailable, setup-required, incompatible, saving, success, failure and stale-revision states have deterministic presentation;
- user-facing failures provide a valid next action when one exists;
- missing source data is shown as unavailable, never zero/placeholder data.

### Platform/accessibility

- compact portrait, compact landscape, medium and expanded layouts preserve content priority;
- expanded layouts use contextual master-detail where useful;
- TalkBack, dynamic text, focus order, 48 dp targets and non-color-only state are covered;
- Back/process recreation re-read source state without persisting sensitive content.

### Design consistency

- existing Harness semantic tokens/components are reused;
- no parallel visual system or raw color/spacing values are introduced without design-system ownership;
- motion is purposeful and reduced-motion safe;
- illustrative mockup values never enter production state.

### Evidence

Automated evidence should include:

- presentation/reducer tests for state mapping;
- ViewModel/effect tests for read, save, conflict and recovery paths;
- navigation/back/restoration tests;
- Compose semantics for representative application, assignment, preset and error states;
- accessibility/touch-target/large-font coverage;
- compact and expanded layout evidence;
- persistence/restart integration for default/custom preset state;
- two-APK representative device evidence for one consumer application when the shared-runtime release gate requires it.

A polished screenshot alone is not completion evidence.
