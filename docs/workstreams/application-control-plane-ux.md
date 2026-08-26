# Applications control-plane UX workstream

Status: active
Document type: workstream-state
Owner: apps/local-llm-phone-test
Canonical scope: workstream.application-control-plane-ux
Read when: coordinating implementation of Applications, assigned-use-case and preset-management UX
Last reviewed: 2026-08-26

Durable behavior: [`../features/application-control-plane-ux.md`](../features/application-control-plane-ux.md). Operational ledger: [`../current-state.md`](../current-state.md). This file owns only temporary execution state, dependencies and write boundaries.

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
- no duplication of HCP consumer-control-plane/runtime ownership;
- no implicit activate/load/download/infer on navigation;
- no prompt/output/private-path persistence;
- suggested presets are not edited in place;
- reads/mutations use neutral ViewModel-facing contracts and canonical host state;
- mutations are explicit, revision-aware and followed by canonical re-read;
- stale revisions fail closed;
- reusable visuals belong in `ui/design-system`, app composition in `apps/local-llm-phone-test`;
- physical/effective-consumer claims require real two-APK evidence where applicable.

The repository-side HCP/control-plane prerequisite is now represented by the canonical `dev` composition. The remaining effectiveness claim is deliberately held behind ACUX-90 physical two-APK evidence.

## Execution DAG

| ID | State | Depends on | Owns / writes | Parallel with | Acceptance |
| --- | --- | --- | --- | --- | --- |
| ACUX-00 | DONE | — | UX spec + workstream | — | IA, nine view contracts, states, adaptive/accessibility and DAG are explicit. |
| ACUX-10 | DONE | ACUX-00 | neutral control-plane UI gateway, mappers, fakes/tests; smallest host contract extension if required | ACUX-20, ACUX-70 | Apps/assignments/presets/default/effective config read through one source; supported mutations are revision-aware; no Room/UI coupling. |
| ACUX-20 | DONE | ACUX-00 | destination/routes/shell; Apps primary nav; preserve Diagnostics via Settings/deep links | ACUX-10, ACUX-70 | Compact/rail Apps navigation, opaque detail routes and side-effect-free Back/restoration are integrated. |
| ACUX-30 | DONE | ACUX-10, ACUX-20 | Applications + Application detail state/presentation/Compose | ACUX-40 | Source-backed loading/empty/populated/error/auth/identity states and app drill-down are integrated. |
| ACUX-40 | DONE | ACUX-10, ACUX-20 | Assigned use case + preset list/detail | ACUX-30 | Default, Suggested/Custom and effective configuration are visible with Advanced/Technical disclosure. |
| ACUX-50 | DONE | ACUX-10, ACUX-40 | custom preset creation/edit + Advanced settings | ACUX-60 if contracts do not collide | Real base preset, canonical validation, guarded save, persisted re-read and exact-value controls are integrated. |
| ACUX-60 | DONE | ACUX-10, ACUX-40 | set-default and assignment mutations only where host supports them | ACUX-50 if contracts do not collide | Supported mutations are explicit, revision-safe and canonically refreshed; unsupported actions are not fabricated. |
| ACUX-70 | DONE | ACUX-00 | only reusable semantic components/tests/previews in `ui/design-system` | ACUX-10, ACUX-20 | Existing semantic components are reused; no speculative shared-component layer was introduced. |
| ACUX-80 | DONE | ACUX-30, ACUX-40, ACUX-50, ACUX-60, ACUX-70 | cross-screen recovery, adaptive master-detail, accessibility, restoration tests | — | Compact and wide-layout policy, large-font reflow, master-detail behavior and repository-side recovery/accessibility coverage are integrated. |
| ACUX-90 | READY | ACUX-80 + canonical HCP consumer-control-plane path | integration/E2E + representative two-APK evidence | — | Persisted default survives restart and is honored by a real consumer with exact app/use-case/binding/preset identity; failure path fails closed. Physical evidence is the only remaining ACUX feature gate. |
| ACUX-100 | BLOCKED | ACUX-90 | durable docs/state transfer + workstream cleanup | — | After ACUX-90, transfer the final device evidence to durable owners and delete this temporary workstream by default. |

## Integrated repository-side baseline

- PR #447 integrated the canonical Applications flow on `dev`: Apps primary navigation, source-backed Application -> Assigned use case -> Preset drill-down, custom preset creation, revision-safe supported mutations and canonical re-read behavior.
- PR #449 integrated ACUX-80 adaptive convergence from a clean current-`dev` base: medium/expanded `Applications list | Selected application` master-detail, compact/large-font single-pane fallback and focused adaptive-policy coverage.
- #449 passed Repository health, Validate and Package Android Artifacts on exact head `625747bcc6ef28a9cd0966a693550444fd4db1ed` before squash merge; canonical `dev` now contains the result at `d8caa3454c51c9c8e53ff3da95d31f7c3df6f1ed`.
- No repository-side Applications implementation task remains before ACUX-90. Do not reopen Waves A-D unless a concrete regression or new product requirement appears.

## Parallel waves

**Wave A — foundations: COMPLETE.** ACUX-10, ACUX-20 and ACUX-70 are integrated.

**Wave B — read surfaces: COMPLETE.** ACUX-30 and ACUX-40 are integrated on stable gateway/UI contracts.

**Wave C — mutations: COMPLETE.** ACUX-50 and ACUX-60 are integrated with revision-aware canonical refresh and no fabricated unsupported mutation.

**Wave D — convergence: COMPLETE.** ACUX-80 adaptive/accessibility/recovery convergence is integrated without introducing new domain policy.

**Wave E — effective proof: NEXT.** ACUX-90 is the only remaining feature gate. Serialize same-device runtime/thermal-sensitive evidence with other physical suites when useful, but preserve the ACUX identity and pass/fail criteria independently.

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

Repository-side convergence is complete. Compact uses the normal single-pane drill-down; medium/expanded uses Applications master-detail when dense-content reflow is not required; large-font policy falls back to the safer single-pane composition. Existing source-backed states and mutation recovery remain the only state owner.

### ACUX-90 E2E

Minimum proof: authorized app visible -> assignment opened -> preset selected/created and set default -> Harness restart -> default still visible -> consumer discovers/activates -> inference uses expected assignment/config identity -> privacy-safe evidence correlates revisions -> invalid/stale case fails closed.

This must run with two real APKs on representative hardware. CI/emulator evidence does not satisfy ACUX-90.

## Validation

Repository-side cumulative gates are complete through ACUX-80. The exact ACUX-80 head passed the repository workflows before merge, including scoped Android compilation/tests/lint/packaging plus repository-health checks.

For future repository-side changes, retain the canonical gate set:

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

Physical device evidence is required only at ACUX-90 for this feature closeout.

## Exit

Repository-side completion is achieved: Apps primary navigation, source-backed app/use-case/preset drill-down, revision-safe supported mutations, progressive Technical details and adaptive/accessibility repository coverage are integrated.

End-to-end completion still requires ACUX-90 effective-consumer proof. After that proof, transfer the evidence/result to the durable feature/current-state/release owners, mark ACUX-100 complete and remove this temporary workstream by default.
