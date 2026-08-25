# Applications control-plane UX workstream

Status: active
Document type: workstream-state
Owner: apps/local-llm-phone-test
Canonical scope: workstream.application-control-plane-ux
Read when: coordinating implementation of Applications, assigned-use-case and preset-management UX
Last reviewed: 2026-08-25

Durable UX behavior is specified in [`../features/application-control-plane-ux.md`](../features/application-control-plane-ux.md). This file owns only temporary implementation sequencing, dependencies, write boundaries and acceptance state.

## Goal

Deliver a source-backed Android experience in which a Harness user can:

```text
see consumer applications
  -> inspect assigned use cases
  -> inspect the effective default preset
  -> choose or create a custom preset
  -> persist the assignment/configuration
  -> verify the effective state
```

without exposing bindings, Room rows or Binder protocol structures as the primary task model.

The implementation must preserve the current Harness design language, shared-runtime ownership, privacy boundaries and revision-safe control-plane semantics.

## Non-goals

- no rewrite of the host control-plane domain/store;
- no second application-local control-plane database;
- no direct Compose-to-Room access;
- no duplication of HCP-21 Binder protocol work from PR #343;
- no duplication of HCP-27 external-consumer cutover work from PR #348;
- no prompt/generated-output persistence;
- no implicit model load/download/inference on navigation;
- no in-place mutation of suggested presets;
- no redesign of Playground/Performance/Models unrelated to the Apps destination and required contextual links.

## Invariants

- `dev` is the base/target for implementation slices; each implementation slice starts from the latest green `dev` after checking competing PRs.
- UI models are immutable and ViewModel-owned.
- Screens use a neutral app-facing gateway; they never call Room DAOs or Binder implementations directly.
- The canonical host control-plane store remains the persistence owner for applications, use cases, bindings, presets and exposures.
- Every displayed value is source-backed or explicitly unavailable.
- Navigation/read refresh is observational and does not activate/load/download/infer.
- Mutations are explicit, revision-aware effects followed by a canonical re-read.
- Suggested presets remain reproducible; customization creates a custom preset revision/identity.
- Stale revisions fail closed and never silently overwrite newer state.
- Prompt/generated output, private paths, document URIs and signed URLs remain outside this feature.
- Shared UI primitives/tokens belong in `ui/design-system`; application composition belongs in `apps/local-llm-phone-test`.
- Physical two-APK evidence cannot be claimed from host tests/emulator evidence.

## External integration dependencies

Two existing HCP lines overlap the runtime-effectiveness boundary but not the product UI ownership:

- PR #343 — HCP-21 consumer control-plane protocol/discovery/activation;
- PR #348 — HCP-27 external-consumer cutover to active control-plane bindings.

The Apps UI must not duplicate these transport/runtime changes. Repository-side read/configuration UI can proceed against canonical host contracts where available, but final **effective consumer configuration** E2E is blocked until the accepted HCP protocol/cutover line is integrated or superseded by equivalent current-`dev` behavior.

Before beginning any code slice, re-check whether #343/#348 are still canonical because both are older stacked branches and may be replaced by newer integration work.

## Execution DAG

| ID | State | Depends on | Owns / writes | Can run with | Acceptance |
| --- | --- | --- | --- | --- | --- |
| ACUX-00 | DONE | — | durable UX spec + this workstream only | — | Task model, IA, view contracts, states, adaptive/accessibility rules and implementation DAG are explicit. |
| ACUX-10 | READY | ACUX-00 | app-facing control-plane gateway contracts, presenters/mappers, deterministic fakes/tests; smallest required neutral domain read/mutation extension if missing | ACUX-20 | Harness can read registered apps, assigned use cases, exposed presets and default/effective configuration through one neutral boundary; supported mutations are revision-aware; no UI/Room coupling. |
| ACUX-20 | READY | ACUX-00 | `HarnessDestination`/route shell/top-level Apps navigation and contextual Diagnostics relocation/deep links | ACUX-10 | Apps is reachable in compact bottom navigation and expanded rail; detail routes hide top-level nav; Back/restoration route arguments are opaque and side-effect free. |
| ACUX-30 | BLOCKED | ACUX-10, ACUX-20 | Applications list + Application detail ViewModel state/presentation/Compose screens | ACUX-40 | Populated/empty/loading/error/disabled/identity-warning states are source-backed; app -> application-detail navigation works; no raw binding IDs dominate normal UI. |
| ACUX-40 | BLOCKED | ACUX-10, ACUX-20 | Assigned-use-case + preset-list + preset-detail screens and mapping | ACUX-30 | Application -> use case -> preset drill-down exposes default, Suggested/Custom origin and effective configuration summary with progressive disclosure. |
| ACUX-50 | BLOCKED | ACUX-10, ACUX-40 | custom-preset creation/edit flow, Advanced inference settings, validation and save effect | ACUX-60 where write paths are separate | Creation starts from a real published base where available; suggested presets are not edited in place; save is guarded, persisted, re-read and recoverable; exact-value controls remain accessible. |
| ACUX-60 | BLOCKED | ACUX-10, ACUX-40 | assignment/default-preset mutations, enable/disable/unassign behavior only where canonical host capability exists | ACUX-50 where write paths are separate | Set-default is one explicit action with revision checks and canonical refresh; assign/unassign/enable/disable appear only for supported host mutations and destructive effects are confirmed. |
| ACUX-70 | READY | ACUX-00 | only genuinely reusable semantic components/tokens in `ui/design-system` + component tests/previews | ACUX-10, ACUX-20 | Existing components are reused first; new components exist only for repeated semantic roles such as identity row, key/value row or recovery panel; light/dark/system semantics remain canonical. |
| ACUX-80 | BLOCKED | ACUX-30, ACUX-40, ACUX-50, ACUX-60, ACUX-70 | cross-screen state/recovery, adaptive master-detail, accessibility semantics, process/back restoration tests | — | Compact/landscape/medium/expanded, TalkBack, dynamic text, 48dp targets, stale-revision recovery and no-color-only state pass deterministic coverage. |
| ACUX-90 | BLOCKED | ACUX-80, accepted HCP effective-consumer line | integration/E2E + representative two-APK device evidence; no new domain ownership | — | A real authorized consumer app uses the persisted app/use-case/default-preset assignment after restart; exact revisions/config identity are observable; failure/recovery paths are evidenced without prompt/output persistence. |
| ACUX-100 | BLOCKED | ACUX-90 | durable docs/current state/cleanup only | — | Durable behavior/evidence is transferred to owning docs, temporary status references are removed and this workstream is deleted by default. |

## Parallel execution policy

### Wave A — foundations

Run in parallel:

- **ACUX-10** — control-plane gateway/state boundary;
- **ACUX-20** — navigation shell;
- **ACUX-70** — only design-system primitives proven necessary by the settled screen semantics.

Write boundaries must remain separate. ACUX-20 must not build fake screen state, and ACUX-70 must not create feature-specific domain components.

### Wave B — primary read surfaces

After ACUX-10/20:

- **ACUX-30** Applications/Application detail;
- **ACUX-40** Assigned use case/Preset detail.

These can run in parallel when each owns separate screen/state files and shares only stable gateway/UI contracts.

### Wave C — mutations

After ACUX-40:

- **ACUX-50** custom preset editor;
- **ACUX-60** default/assignment mutations.

Run concurrently only if their mutation ownership does not collide. If both require the same host control-plane contract change, land that smallest shared contract change first and then split UI/effect work.

### Wave D — convergence

**ACUX-80** integrates responsive behavior, state recovery, accessibility and restoration across the completed vertical slices. Do not use this slice to introduce new domain behavior.

### Wave E — effective consumer proof

**ACUX-90** runs only after the accepted HCP consumer control-plane/cutover behavior is in current `dev`. Same-device physical Binder/runtime evidence is serialized with other thermal/residency-sensitive suites.

## Detailed slice requirements

### ACUX-10 — app-facing control-plane gateway

Required read model:

- registered application identity/display/package/status;
- first/last-seen or availability evidence when the canonical source supports it;
- assigned use cases per application;
- use-case description/state/revision;
- published/exposed presets per assignment;
- Suggested/Custom origin;
- default preset identity;
- effective model-selection/configuration summary;
- technical IDs/revisions behind an expert projection.

Required mutation model where canonical host capability exists:

- set default preset using expected revisions;
- create custom preset from a canonical base;
- edit custom preset by new revision or accepted update semantics;
- enable/disable assignment;
- assign/unassign use case.

If any mutation is not supported by the canonical host contracts, the implementation must either add the smallest neutral host-owned capability with focused tests or omit the UI action. It must never update Room directly from the app screen layer.

### ACUX-20 — navigation

Target compact primary destinations:

```text
Overview | Playground | Apps | Performance | Models
```

Diagnostics remains reachable through Settings/Developer tools and contextual deep links. Preserve existing diagnostic routes; this change is navigation hierarchy, not removal of diagnostics functionality.

Detail route concepts follow the durable feature spec and use opaque arguments.

### ACUX-30 — Applications + Application detail

Minimum states:

- loading;
- no applications;
- populated;
- source unavailable/error;
- authorized;
- disabled;
- unavailable;
- identity/signature warning where source-backed.

Application row and detail composition follow the durable specification. No illustrative values are committed as live app data.

### ACUX-40 — Assignment + preset read surfaces

Must make these decisions visually obvious:

1. which app/use case is being configured;
2. whether the assignment is active/usable;
3. which preset is default;
4. whether a preset is Suggested or Custom;
5. what model/configuration policy it represents at summary level;
6. where Advanced and Technical details live.

### ACUX-50 — custom preset editor

Use `good default -> customization -> expert override`.

A source preset is required when the domain provides suggested presets. `Custom from scratch` is expert-only and must not be the default path.

Validation belongs to neutral config/domain validators where rules already exist. Compose displays validation; it must not invent a second inference-policy validator.

### ACUX-60 — assignment/default mutations

For each mutation define:

```text
action
 -> immediate acknowledgement
 -> revision-aware host mutation
 -> terminal success/failure
 -> canonical re-read
 -> next action/recovery
```

Stale revision never silently retries. Destructive unassignment/custom-preset deletion requires explicit consequence copy.

### ACUX-70 — shared components

Potential shared roles, only if existing components cannot express them cleanly:

- `HarnessIdentityRow`;
- `HarnessKeyValueRow`;
- `HarnessRecoveryPanel`;
- `HarnessSelectableSummaryRow`.

Names are illustrative implementation candidates, not pre-approved mandatory components. Prefer composition from existing primitives where that is sufficient.

### ACUX-80 — accessibility/adaptive/recovery

Required automated matrix:

- compact portrait;
- compact landscape;
- medium/expanded master-detail;
- large font/dynamic text;
- TalkBack semantics/focus order;
- semantic status not color-only;
- stale revision;
- save failure/retry;
- unavailable capability/source;
- process recreation and Back-stack restoration.

No sensitive data may be added to saved state to make restoration tests pass.

### ACUX-90 — representative effective-config E2E

Minimum representative journey:

1. Harness recognizes an authorized consumer app;
2. user opens the app and assigned use case;
3. user selects/creates a preset and makes it default;
4. Harness is restarted/recreated;
5. persisted default remains visible;
6. consumer app discovers/activates the assignment through the accepted control-plane protocol;
7. one inference executes through the expected assignment/config identity;
8. Harness evidence correlates app/use-case/binding/preset revisions without persisting prompt/output;
9. an invalid/stale configuration path fails closed with actionable recovery.

This slice proves wiring, not general device performance.

## Validation commands

Each code slice selects the narrowest relevant repository gates. Expected cumulative repo-side commands include:

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

Add owning control-plane module tests when ACUX-10/50/60 touch shared contracts or persistence. Add Compose instrumentation/E2E for ACUX-30/40/50/80. Physical evidence remains a separate gate under ACUX-90.

## Definition of Done

Repository-side UX implementation is complete when:

- Apps is a primary destination with deterministic compact/expanded navigation;
- Applications and Application detail are source-backed;
- app -> use case -> preset drill-down works;
- default, Suggested/Custom and effective configuration are obvious;
- custom preset creation/editing is revision-safe and persistence-backed;
- supported assignment/default mutations are explicit and recoverable;
- technical IDs/revisions are progressively disclosed;
- all critical state/adaptive/accessibility tests are green;
- no prompt/output/private path leaks were introduced.

The end-to-end feature is complete only when ACUX-90 proves a persisted assignment/default preset is honored by a real consumer through the accepted shared-runtime control-plane path on representative physical hardware where required.

## Durable destinations on completion

Transfer final behavior/evidence to:

- [`../features/application-control-plane-ux.md`](../features/application-control-plane-ux.md) for durable user behavior;
- [`../features/phone-app-architecture.md`](../features/phone-app-architecture.md) if state/effect/navigation ownership changes materially;
- [`../harness-ux-ui-implementation-plan.md`](../harness-ux-ui-implementation-plan.md) for top-level product target;
- [`../../design/ux-contract.json`](../../design/ux-contract.json) for critical journey/experience contract;
- HCP/shared-runtime durable architecture/roadmap owners for any accepted domain/protocol changes;
- [`../current-state.md`](../current-state.md) for only the current integrated state and remaining blockers.

When ACUX-100 completes, delete this temporary workstream by default. Git history retains execution history.
