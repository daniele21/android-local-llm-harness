# Applications control-plane UX workstream

Status: active
Document type: workstream-state
Owner: apps/local-llm-phone-test
Canonical scope: workstream.application-control-plane-ux
Read when: coordinating implementation of Applications, assigned-use-case and preset-management UX
Last reviewed: 2026-08-25

Durable behavior: [`../features/application-control-plane-ux.md`](../features/application-control-plane-ux.md). This file owns only temporary execution state, dependencies and write boundaries.

## Goal

Deliver a source-backed Android flow:

```text
Applications -> Application -> Assigned use case -> Default/effective preset
             -> choose/create preset -> persist -> verify
```

without exposing bindings, Room rows or Binder protocol as the primary task model.

## Non-goals and invariants

- no rewrite/duplicate of the host control-plane store;
- no direct Compose-to-Room/Binder access;
- no duplication of HCP-21 PR #343 or HCP-27 PR #348;
- no implicit activate/load/download/infer on navigation;
- no prompt/output/private-path persistence;
- suggested presets are not edited in place;
- reads/mutations use neutral ViewModel-facing contracts and canonical host state;
- mutations are explicit, revision-aware and followed by canonical re-read;
- stale revisions fail closed;
- reusable visuals belong in `ui/design-system`, app composition in `apps/local-llm-phone-test`;
- physical/effective-consumer claims require real two-APK evidence where applicable.

PRs #343/#348 currently own overlapping transport/runtime effectiveness. Re-check their canonical status before implementation; final effective-consumer E2E waits for their accepted or superseding current-`dev` line.

## Execution DAG

| ID | State | Depends on | Owns / writes | Parallel with | Acceptance |
| --- | --- | --- | --- | --- | --- |
| ACUX-00 | DONE | — | UX spec + workstream | — | IA, nine view contracts, states, adaptive/accessibility and DAG are explicit. |
| ACUX-10 | READY | ACUX-00 | neutral control-plane UI gateway, mappers, fakes/tests; smallest host contract extension if required | ACUX-20, ACUX-70 | Read apps/assignments/presets/default/effective config through one source; supported mutations are revision-aware; no Room/UI coupling. |
| ACUX-20 | READY | ACUX-00 | destination/routes/shell; Apps primary nav; preserve Diagnostics via Settings/deep links | ACUX-10, ACUX-70 | Compact/rail Apps navigation, opaque detail routes and side-effect-free Back/restoration. |
| ACUX-30 | BLOCKED | ACUX-10, ACUX-20 | Applications + Application detail state/presentation/Compose | ACUX-40 | Source-backed loading/empty/populated/error/auth/identity states and app drill-down. |
| ACUX-40 | BLOCKED | ACUX-10, ACUX-20 | Assigned use case + preset list/detail | ACUX-30 | Default, Suggested/Custom and effective configuration are obvious with Advanced/Technical disclosure. |
| ACUX-50 | BLOCKED | ACUX-10, ACUX-40 | custom preset creation/edit + Advanced settings | ACUX-60 if contracts do not collide | Real base preset, canonical validation, guarded save, persisted re-read, accessible exact-value controls. |
| ACUX-60 | BLOCKED | ACUX-10, ACUX-40 | set-default and assignment enable/disable/assign/unassign where host supports it | ACUX-50 if contracts do not collide | Explicit revision-safe mutation, recovery/confirmation and canonical refresh. Unsupported mutations do not get fake UI. |
| ACUX-70 | READY | ACUX-00 | only reusable semantic components/tests/previews in `ui/design-system` | ACUX-10, ACUX-20 | Reuse first; new shared components only for repeated semantic roles. |
| ACUX-80 | BLOCKED | ACUX-30, ACUX-40, ACUX-50, ACUX-60, ACUX-70 | cross-screen recovery, adaptive master-detail, accessibility, restoration tests | — | Compact/landscape/medium/expanded, TalkBack/large text, 48dp, stale conflict and process/Back behavior pass. |
| ACUX-90 | BLOCKED | ACUX-80 + accepted HCP effective-consumer line | integration/E2E + representative two-APK evidence | — | Persisted default survives restart and is honored by a real consumer with exact app/use-case/binding/preset identity; failure path fails closed. |
| ACUX-100 | BLOCKED | ACUX-90 | durable docs/state transfer + workstream cleanup | — | Durable owners updated; temporary workstream deleted by default. |

## Parallel waves

**Wave A — foundations:** run ACUX-10, ACUX-20 and ACUX-70 in parallel. Keep gateway, navigation and design-system writes separate.

**Wave B — read surfaces:** after ACUX-10/20, run ACUX-30 and ACUX-40 in parallel using stable gateway/UI contracts.

**Wave C — mutations:** after ACUX-40, run ACUX-50 and ACUX-60 concurrently only if they do not require competing changes to the same host contract. Land the smallest shared contract first when they do.

**Wave D — convergence:** ACUX-80 integrates state/recovery/adaptive/accessibility; it must not introduce new domain policy.

**Wave E — effective proof:** ACUX-90 starts only from the accepted current-`dev` HCP path. Serialize same-device runtime/thermal-sensitive evidence with other physical suites.

## Slice detail

### ACUX-10 gateway

Required projection: app identity/status/recency where available; assignments; use-case state/revision; exposed presets; Suggested/Custom; default preset; effective model/config summary; expert IDs/revisions. Mutations: set default, create/update custom preset, and assignment lifecycle only where canonical host contracts support them. Missing capability means smallest neutral host extension or omitted action, never direct DAO access.

### ACUX-20 navigation

Target compact primary destinations:

```text
Overview | Playground | Apps | Performance | Models
```

Diagnostics functionality remains available from Settings/Developer tools/contextual links. Detail routes use bounded opaque identifiers.

### ACUX-30/40 views

Must make four decisions immediately visible: which app, which use case, which preset is default, and what effective model/config policy it represents. Raw binding/preset revisions remain under Technical details.

### ACUX-50/60 mutations

Use:

```text
action -> acknowledgement -> revision-aware mutation -> outcome -> canonical re-read -> recovery/next action
```

No silent stale retry. Suggested preset customization creates Custom identity. Destructive unassignment/delete names consequences before execution.

### ACUX-80 matrix

Automate representative compact portrait, landscape, medium/expanded master-detail, large text, TalkBack/focus, non-color status, loading/empty/error/saving/success, stale revision, unavailable capability and process/back restoration. Sensitive content never enters saved state.

### ACUX-90 E2E

Minimum proof: authorized app visible -> assignment opened -> preset selected/created and set default -> Harness restart -> default still visible -> consumer discovers/activates -> inference uses expected assignment/config identity -> privacy-safe evidence correlates revisions -> invalid/stale case fails closed.

## Validation

Expected repo-side cumulative gates:

```bash
./gradlew spotlessCheck
./gradlew --no-configuration-cache detekt verifyNoModelArtifacts
./gradlew :apps:local-llm-phone-test:compileDebugKotlin \
  :apps:local-llm-phone-test:compileDebugUnitTestKotlin \
  :apps:local-llm-phone-test:testDebugUnitTest \
  :apps:local-llm-phone-test:lintDebug \
  :apps:local-llm-phone-test:assembleDebug
python3 scripts/verify-agent-navigation.py
python3 scripts/verify-documentation.py
```

Add owning control-plane module tests for shared contract/store changes, Compose instrumentation for UI slices and physical device evidence only at ACUX-90.

## Exit

Repository-side completion requires Apps primary navigation, source-backed app/use-case/preset drill-down, revision-safe supported mutations, progressive Technical details, adaptive/accessibility tests and no privacy-boundary regressions.

End-to-end completion additionally requires ACUX-90 effective-consumer proof. Transfer durable results to the feature spec, phone architecture if ownership changed, UX target/contract, HCP durable owners and `current-state.md`; then remove this temporary workstream.
