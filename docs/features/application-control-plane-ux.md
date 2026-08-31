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

The UI translates the host control-plane domain into a task-first product model. Room rows, Binder transactions, bindings and revisions stay out of the primary information architecture unless they create troubleshooting value.

Governing owners: [`../../design/ux-contract.json`](../../design/ux-contract.json), [`../../design/brand-kit.json`](../../design/brand-kit.json), [`../harness-ux-ui-implementation-plan.md`](../harness-ux-ui-implementation-plan.md), `ui/design-system`. Temporary sequencing belongs in [`../workstreams/application-control-plane-ux.md`](../workstreams/application-control-plane-ux.md).

## User outcome and constraints

Primary user: Android/AI engineer using Harness as the shared local runtime for one or more consumer apps.

Successful task model:

```text
App connection
  -> Assigned use case
  -> Default preset
  -> Effective model/configuration policy
```

The user can create and suspend app access, inspect assignments, choose/customize presets and verify effective configuration without interpreting binding IDs, preset exposures, Binder protocol or Room entities.

Non-goals:

- no replacement for Playground experimentation;
- no raw Binder/Room control UI;
- no second control-plane store;
- no implicit model load/download/inference on navigation;
- no prompt/generated-output persistence;
- no in-place mutation of suggested presets;
- no package/signer guessing: app authorization requires exact source-backed identity;
- no usability/compatibility claim without source-backed evidence.

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

Target compact primary navigation:

```text
Overview | Playground | Apps | Performance | Models
```

Diagnostics remains available through Settings/Developer tools and contextual deep links. Expanded layouts use the existing navigation rail.

Drill-down:

```text
App connections
  -> New app connection
  -> Application detail
      -> enable/disable connection
      -> Assigned use case
          -> Preset detail
              -> Advanced settings
              -> Technical details
          -> Create custom preset
      -> Assign use case (only if supported)
```

A separate top-level Bindings screen is not part of this design.

## Critical journeys

### App connection creation

```text
App connections
 -> New app connection
 -> exact app identity (name + Harness application ID + package + signer SHA-256)
 -> choose active use case
 -> choose initial published preset
 -> review
 -> Create & enable connection
 -> canonical re-read
 -> enabled connection
```

Creation is a security-sensitive mutation. The package and signer are not advisory metadata: they feed the Binder authorization boundary. A successful connection is enabled immediately; disabling it later blocks Binder authorization while retaining assignments and preset configuration.

### Consumer configuration

```text
App connections
 -> application
 -> assigned use case
 -> current default preset
 -> existing preset OR create custom preset
 -> review effective configuration
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

### V1 — App connections

Goal: identify apps using Harness, their high-level state and provide the real connection-creation entry point.

Header: `App connections` + `Control which Android apps can use the Harness shared runtime.`

Primary action: `New app connection` because a real persisted registration/authorization mutation exists.

Each row shows, when source-backed:

- display name and package;
- Enabled/Disabled/identity/availability state;
- activity recency such as `Active now` when source-backed;
- assigned-use-case count;
- navigation affordance.

Do not show application ID, signer hash or revision here.

Empty copy: `No applications connected` plus a direct explanation that the user can create a connection to authorize an app package, signer and use case.

### V2 — New app connection

Goal: create one explicit, reviewable authorization relationship without exposing internal Binder mechanics as the primary task.

Sections:

1. `Application identity`: display name, stable Harness application ID, exact Android package and signing-certificate SHA-256;
2. `Use case`: one active use case that has at least one published preset;
3. `Initial preset`: the preset that becomes the default for the first assignment;
4. `Review connection`: app, package, use case, default preset and the consequence that creation enables access immediately.

Rules:

- SHA-256 accepts pasted spaces/colon separators but normalizes to exactly 64 hexadecimal characters before save;
- duplicate application IDs or Android packages are rejected fail-closed;
- use cases/presets come from the canonical control plane, never illustrative UI constants;
- creation persists application + assignment + default preset exposure atomically;
- success is final only after canonical re-read;
- the primary action is `Create & enable connection`.

### V3 — Application detail

Goal: answer **can this app connect, and what does it use Harness for?**

Header block: app name, package, semantic status and recency.

A dedicated `Allow app connection` switch appears for reversible Authorized/Disabled states. Its copy states the consequence:

- Enabled: the app can authenticate to the shared runtime for assigned use cases;
- Disabled: Binder access is blocked while configuration is retained.

The switch is disabled while saving or when identity state requires recovery. The mutation is persisted and followed by canonical re-read; it is not UI-only state.

Primary content: Assigned use cases. Each row shows use-case name, concise purpose, default preset, optional model-policy summary, state and navigation affordance.

Contextual `Connection` information may include first seen, last seen and assignment count. `Assign use case` appears only with a real host mutation capability. `Technical details` contains application identity/signer/revision.

### V4 — Assigned use case

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

### V5 — Preset detail

Goal: explain what the preset represents and what can safely change.

Summary-first information:

- preset name, Suggested/Custom, Default;
- source-backed description;
- model-selection policy;
- context size, max output tokens, thinking mode;
- runtime/warm policy summary where available.

Suggested presets are immutable in normal UI. Actions: `Use as default` when applicable and `Duplicate & customize` when supported.

Custom presets may expose Edit, Set default, Duplicate and Delete according to domain support. Referenced-preset deletion is prevented or explains required reassignment.

`Advanced settings` and `Technical details` are progressive disclosures.

### V6 — Create preset

Goal: create a usable custom preset while making the parameters the user is actually configuring editable.

Default path starts from a real published preset, preferably the current default. The editor follows **base + overrides** semantics: blank optional numeric fields inherit the selected base preset; values entered by the user become persisted generation overrides.

Hierarchy:

```text
Essential: name + base preset + model target
Contextual: context + max output + thinking + temperature + top-p
Advanced: top-k + min-p + penalties + repeat window + seed policy/value
Review: effective generation configuration after overrides
```

Editable generation parameters supported by the canonical contract are:

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

`Advanced settings · Show/Hide` keeps expert parameters out of the default path. `Reset generation overrides` returns generation behavior to inheritance from the base preset.

Every numeric control uses the shared `HarnessNumberField` contract. Integer/decimal keyboards, filtering and separator normalization come from the design system; domain ranges are validated inline by the preset domain. A generic text field must not be used for numeric parameters.

The `Effective generation configuration` preview applies the draft overrides over the canonical base generation profile, including tier-specific defaults when the model target remains automatic.

Primary action: `Save preset`.

Save contract:

```text
validate -> revision-aware persist -> canonical re-read -> success/failure
```

The saved `PresetExecutionPolicy` owns the overrides and activation applies them over the canonical inference preset. No optimistic-only success and no UI-only generation state.

### V7 — Preset saved

Goal: confirm a durable result and expose the next decision.

Show success semantics, `Preset saved`, preset name and concise confirmation.

Next actions: `Set as default` when supported/not already default, `View preset`, `Done`/Back.

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
| empty apps | explain connection creation and expose the valid action |
| empty assignments | explain no assignment; offer Assign only if supported |
| populated | normal hierarchy |
| disabled/unavailable | state label + valid recovery when available |
| identity changed | warning + identity review/re-authorization path |
| no presets | explain gap + create/recovery path |
| setup required/incompatible | user-language reason + smallest recovery action |
| saving | prevent duplicate submission; preserve form state |
| success | acknowledge + canonical re-read + next action |
| failure | preserve edits + actionable error + Retry/review where valid |
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
App connections list | Selected application detail
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
- numeric fields expose field labels, range/error supporting text and appropriate keyboard intent;
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

Reuse existing `HarnessCard`, `HarnessStatusBadge`, `HarnessNumberField`, primary/secondary actions, detail top bar and shell patterns. Add a shared component only for a repeated semantic role that existing primitives cannot express. App-specific composition stays in `apps/local-llm-phone-test`; reusable primitives stay in `ui/design-system`.

## Motion

Optional and restrained: navigation continuity, pressed/selection feedback, expand/collapse, saving progress and brief status transition only. Do not animate rows merely because data refreshed. Reduced-motion may use immediate state/simple fade.

## Privacy, navigation and ownership

Never display/persist prompts, generated output, private filesystem paths, document URIs, signed model/download URLs or arbitrary backend exceptions.

Opening Apps/app/use-case/preset is observational only: no model activation/load/download/inference. Explicit enable/disable/create/save actions are mutations and are visibly distinguished from navigation.

Target route concepts:

- `applications`;
- `applications/new`;
- application detail;
- application/use-case detail;
- preset detail;
- new preset;
- technical-detail child route where needed.

Route arguments use bounded opaque IDs, not package/signer/domain serialization. Process recreation re-reads canonical source state.

Compose renders immutable ViewModel state. Screens never access Room/control-plane repositories directly. Explicit mutations run through the Activity-scoped Applications ViewModel and neutral gateways, and re-read canonical host state before success is final.

## Acceptance

Task/IA:

- Apps is discoverable from compact/expanded primary shell;
- `New app connection` creates a real persisted authorization relationship;
- application detail can enable/disable Binder access while retaining configuration;
- app -> assigned use case -> preset drill-down is understandable without raw bindings;
- current Default and Suggested/Custom origin are obvious;
- technical IDs/revisions are available but progressively disclosed.

Mutation/state:

- connection creation and enable/disable are explicit, persisted and canonical-re-read;
- disabling a connection removes it from live Binder authorization without deleting its assignment/preset configuration;
- set-default and custom-preset save are explicit, guarded, persisted and re-read;
- custom preset generation overrides are editable, persisted and applied at activation;
- suggested presets are never edited in place;
- stale revisions cannot overwrite newer state;
- loading, empty, unavailable, setup/incompatible, saving, success, failure and stale states are deterministic.

Platform/design:

- all user-editable numeric fields use `HarnessNumberField`, with domain-specific validation kept by the owning feature;
- compact portrait/landscape and medium/expanded preserve priority;
- TalkBack, large text, focus, 48dp targets and non-color-only status pass;
- existing Harness tokens/components are reused;
- no illustrative mockup values enter production state.

Evidence:

- gateway/domain tests for create/enable/disable and configuration retention;
- live Binder policy projection tests;
- preset override persistence/Room round-trip tests;
- runtime activation tests proving overrides reach executed generation defaults;
- navigation/Back/restoration tests;
- Compose semantics + accessibility/adaptive coverage;
- repository STRONG automated preflight on the exact branch HEAD;
- representative two-APK device evidence only when a shared-runtime release/effective-configuration claim requires real-device proof.

A screenshot alone is not completion evidence.
