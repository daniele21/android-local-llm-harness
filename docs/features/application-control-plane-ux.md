# Applications control-plane UX

Status: active
Document type: feature-specification
Owner: apps/local-llm-phone-test
Canonical scope: feature.application-control-plane-ux
Read when: implementing or changing the Harness UI for consumer applications, assigned use cases or inference presets
Last reviewed: 2026-08-31

## Purpose

Define the durable Android experience for answering:

> Which applications can use Harness, for which use cases, and which inference configuration will each application/use-case pair actually use?

The UI translates the host control-plane domain into a task-first product model. Room rows, Binder transactions, bindings and revisions stay secondary unless they provide troubleshooting value.

Governing owners: [`../../design/ux-contract.json`](../../design/ux-contract.json), [`../../design/brand-kit.json`](../../design/brand-kit.json), [`../harness-ux-ui-implementation-plan.md`](../harness-ux-ui-implementation-plan.md), `ui/design-system`. Temporary sequencing belongs in [`../workstreams/application-control-plane-ux.md`](../workstreams/application-control-plane-ux.md).

## User outcome and invariants

Primary user: Android/AI engineer using Harness as the shared local runtime for one or more consumer apps.

Task model:

```text
App connection
  -> Assigned use case
  -> Default preset
  -> Effective model/configuration policy
```

The user can create and suspend app access, inspect assignments, choose/customize presets and verify effective configuration without interpreting internal persistence or Binder protocol structures.

Invariants:

- Harness remains the canonical owner of application authorization, assignments and presets;
- package and signer identity are exact source-backed security inputs, not descriptive metadata;
- navigation and inspection are side-effect free: no model load, download or inference;
- connection creation, enable/disable and preset changes are explicit mutations followed by canonical re-read;
- suggested presets are never edited in place;
- stale revisions never silently overwrite newer state;
- no prompt, generated output, private path, document URI or signed URL enters this UI state;
- no illustrative/mock data is promoted into production state.

## Terminology

| Internal concept | Primary UI term | Raw term location |
| --- | --- | --- |
| registered application | App connection / Application | Technical details |
| `ApplicationUseCaseBinding` | Assigned use case / Assignment | Technical details |
| preset exposure | Available preset | Technical details if useful |
| suggested/custom preset | Suggested / Custom preset | Normal UI |
| default binding/preset state | Default configuration / Default preset | Normal UI |
| activation | Active / In use when source-backed | Contextual state |
| IDs/revisions/signer | Technical details | Expert disclosure |

`Binding` is not a primary navigation label.

## Information architecture

Compact primary navigation:

```text
Overview | Playground | Apps | Performance | Models
```

Diagnostics stays in Settings/Developer tools and contextual deep links. Expanded layouts use the existing navigation rail.

Apps drill-down:

```text
App connections
  -> New app connection
  -> Application detail
      -> enable/disable connection
      -> Assigned use case
          -> Preset detail
          -> Create custom preset
```

`Assign use case`, edit/delete preset and other mutations appear only when a real host capability exists. A separate top-level Bindings screen is not part of the design.

## Critical journeys

### Create an app connection

```text
App connections
 -> New app connection
 -> exact app identity
 -> choose active use case
 -> choose initial published preset
 -> review
 -> Create & enable connection
 -> canonical re-read
 -> enabled connection
```

Identity consists of a human label, stable Harness application ID, exact Android package and signing-certificate SHA-256. Creation persists the application, first assignment and default preset exposure atomically. The connection is authorized immediately only after successful persistence and re-read.

Disabling later blocks Binder authorization while retaining assignments and preset configuration.

### Configure a consumer

```text
application
 -> assigned use case
 -> current default preset
 -> existing preset OR create custom preset
 -> review effective configuration
 -> save/set default
 -> canonical re-read
 -> verified effective state
```

Success means persisted canonical state, never optimistic UI-only state.

### Recover

```text
unavailable/incompatible/stale state
 -> explain the source-backed problem
 -> smallest valid recovery action
 -> canonical re-read
 -> recovered state or bounded failure
```

Examples include `Reload changes`, `Retry`, `View compatible models` and identity review when the domain supports them. No hidden retry may turn a stale write into a newer write.

## View contracts

### V1 — App connections

Goal: identify apps using Harness and expose real connection creation.

Header: `App connections` with concise access-control explanation.

Primary action: `New app connection`.

Each row shows, when source-backed:

- display name and package;
- Enabled/Disabled/identity/availability state;
- useful activity state or recency;
- assigned-use-case count;
- navigation affordance.

Application ID, signer hash and revisions stay out of the list. Empty state explains that a connection authorizes an exact app identity and use case.

### V2 — New app connection

Sections:

1. `Application identity`: display name, Harness application ID, Android package and signer SHA-256;
2. `Use case`: one active use case with an available published preset;
3. `Initial preset`: default preset for the first assignment;
4. `Review connection`: app, package, use case, default preset and enablement consequence.

Rules:

- pasted SHA-256 may contain spaces/colon separators but normalizes to exactly 64 hexadecimal characters;
- duplicate application IDs or packages fail closed;
- use cases and presets come from the canonical control plane;
- creation is atomic and success requires canonical re-read;
- primary action is `Create & enable connection`.

### V3 — Application detail

Goal: answer **can this app connect, and what does it use Harness for?**

Show app name, package, semantic status, source-backed activity context and assigned use cases.

`Allow app connection` is available only for reversible Authorized/Disabled states:

- Enabled: the app may authenticate to the shared runtime for its assignments;
- Disabled: Binder access is blocked while configuration remains intact.

The switch is unavailable while saving or when identity state requires recovery. Every mutation persists before the UI reports success.

Technical details may include application ID, signer fingerprint and first/last seen timestamps.

### V4 — Assigned use case

Goal: answer **which configuration will this app use for this use case?**

`Default configuration` is the dominant summary and shows preset name, Suggested/Custom origin, model-selection policy, context size and concise purpose when available.

`Available presets` contains only canonical published/exposed presets. Setting default is one explicit revision-aware action followed by canonical re-read.

Never infer claims such as `faster` or `quality` from a preset name alone.

### V5 — Preset detail

Show source-backed description, Suggested/Custom origin, Default state, model-selection policy, context size, generation summary and runtime/warm policy where available.

Suggested presets are immutable. Custom-preset actions appear only when supported by the domain. Advanced and technical values use progressive disclosure.

### V6 — Create preset

Goal: create a usable custom preset while allowing the user to edit the parameters being configured.

The editor starts from a real published preset and uses **base + overrides** semantics. Blank optional numeric values inherit the base; entered values become persisted generation overrides.

Hierarchy:

```text
Essential: name + base preset + model target
Runtime: context + max output + thinking
Sampling: temperature + top-p
Advanced: top-k + min-p + penalties + repeat window + seed
Review: effective generation configuration after overrides
```

Editable generation parameters:

- max output tokens;
- thinking mode (`Base`, `Off`, `On`);
- temperature;
- top-p;
- top-k;
- min-p;
- presence penalty;
- repeat penalty;
- repeat-last-N;
- seed policy (`Base`, `Random`, `Fixed`) and fixed seed.

`Advanced settings · Show/Hide` keeps expert controls out of the default path. `Reset generation overrides` restores inheritance.

All numeric editing uses `HarnessNumberField`. The design system owns keyboard intent, numeric filtering and comma-to-dot normalization; the owning preset domain owns range and cross-field validation.

The effective preview applies draft overrides over the canonical base generation profile, including tier-specific defaults when model selection remains automatic.

Save contract:

```text
validate -> revision-aware persist -> canonical re-read -> success/failure
```

The saved `PresetExecutionPolicy` owns the overrides and activation applies them over the canonical inference preset.

## Hierarchy and actions

Across views:

```text
essential: object identity + current state
contextual: effective configuration + activity
advanced: generation/runtime parameters
expert: IDs + revisions + signer + diagnostics
```

Use existing Harness primary/secondary/destructive semantics. Do not place unrelated filled primary actions on one surface. Prefer spacing and proximity before additional containers.

## State and recovery matrix

| State | Required UX |
| --- | --- |
| loading | bounded progress; avoid flicker |
| empty apps | explain connection creation and expose the valid action |
| empty assignments | explain the gap; offer Assign only if supported |
| populated | normal hierarchy |
| disabled/unavailable | semantic state + valid recovery when available |
| identity changed | warning + identity review/re-authorization path |
| no presets | explain the gap + valid creation/recovery path |
| setup required/incompatible | user-language reason + smallest recovery action |
| saving | prevent duplicate submission; preserve safe form state |
| success | acknowledge + canonical re-read + next action |
| failure | preserve edits + actionable error |
| stale revision | never overwrite; `Configuration changed elsewhere` + `Reload changes` |
| permission denied | explain authorization boundary; no hidden retry |
| destructive action | name exactly what is removed and retained |

Error copy answers what failed, why when known, whether anything changed and what to do next. Technical codes remain secondary.

## Adaptive and accessibility contract

Compact portrait uses single-pane drill-down with deterministic Back. Compact landscape preserves the same priority without shrinking touch targets. Medium/expanded may use `App connections list | Selected application detail`; deeper details replace or extend the detail pane only when width supports it.

Selection, non-sensitive unsaved form state and scroll position follow existing ViewModel/saved-state policy across ordinary window changes.

Accessibility requirements:

- minimum 48dp targets;
- TalkBack labels include object and state;
- status is never color-only;
- logical focus follows task order;
- dynamic text does not clip primary labels/actions;
- toggles announce state and consequence;
- numeric fields expose labels, range/error supporting text and keyboard intent;
- validation errors are associated with fields and announced;
- material success/error changes are announced;
- reduced motion preserves meaning.

## Visual/design-system contract

Reuse `HarnessTheme`, Material semantic surfaces, Harness typography/spacing/shapes, semantic status tones, `HarnessCard`, `HarnessStatusBadge`, `HarnessNumberField`, shared actions and detail/shell patterns.

No gradients, glow, glass, particles, decorative 3D graphs or celebratory animation. Functional state and evidence precede decoration.

App-specific composition stays in `apps/local-llm-phone-test`; reusable primitives stay in `ui/design-system`. Add a shared component only for a repeated semantic role existing primitives cannot express.

## Privacy, navigation and ownership

Opening Apps/app/use-case/preset is observational only. Explicit create/enable/disable/save actions are visibly distinguished from navigation.

Route concepts:

- `applications`;
- `applications/new`;
- application detail;
- application/use-case detail;
- preset detail;
- new preset;
- technical-detail child route when needed.

Route arguments use bounded opaque IDs, not package/signer/domain serialization. Process recreation re-reads canonical state.

Compose renders immutable ViewModel state. Screens never access Room/control-plane repositories directly. Mutations run through the Activity-scoped Applications ViewModel and neutral gateways.

## Acceptance

The feature is complete only when all applicable evidence agrees:

- Apps is discoverable from compact and expanded navigation;
- `New app connection` persists an exact authorization relationship;
- application detail can enable/disable Binder access while retaining configuration;
- app -> use case -> preset drill-down works without exposing raw bindings as the primary model;
- custom generation overrides are editable, persisted and applied during activation;
- every user-editable numeric value uses `HarnessNumberField` with domain validation;
- create/enable/disable/default/custom-preset mutations re-read canonical state before success;
- stale revisions fail closed;
- gateway/domain, Binder-policy, persistence and runtime-activation tests cover the new invariants;
- adaptive/accessibility semantics remain intact;
- the repository-selected STRONG automated preflight passes on the exact branch HEAD.

Representative two-APK device evidence remains a separate `REAL_ENVIRONMENT` requirement only when a release/effective-runtime claim needs physical-device proof. A screenshot alone is not completion evidence.
