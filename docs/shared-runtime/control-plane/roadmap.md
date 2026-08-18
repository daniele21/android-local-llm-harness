# Harness Host Control Plane roadmap

Status: active
Document type: roadmap
Owner: shared-runtime-control-plane
Canonical scope: shared-runtime.control-plane.roadmap
Read when: selecting the next Host Control Plane task, checking dependencies, or defining parallel implementation slices
Last reviewed: 2026-08-18

This roadmap owns the implementation sequence for turning Harness into the configurable host control plane for internal and external local-AI execution. Detailed durable semantics live in [`README.md`](README.md) and ADR 0015. Shared-runtime security/release evidence remains owned by the existing shared-runtime roadmap.

## Goals

The completed workstream must provide all of the following together:

- Harness-defined persistent use cases rather than consumer-specific hardcoded bindings;
- suggested presets generated/configured in Harness plus user-created custom presets;
- explicit publication/exposure of presets to each consumer application;
- deterministic application + use case + preset revision -> model/execution resolution;
- activation leases that protect model residency across multiple sessions and idle gaps;
- one persistent session/inference history for Harness-internal and Binder-consumer execution;
- an in-app decision center backed by structured decision events, with selected actionable events delivered as Android notifications;
- a consumer API that exposes only assigned use cases and published preset metadata;
- RedactGuard and future consumers free of concrete model/binding/runtime ownership.

## Dependency map

```text
HCP-0 architecture freeze
   |
   |----------------------------|-----------------------------|
   v                            v                             v
HCP-1 app registry          HCP-2 use cases               HCP-10 decision events
   |                            |                             |
   |                       |----+----|                        v
   |                       v         v                     HCP-11 rules
   |                     HCP-3     HCP-4                      |
   |                   suggestions custom presets              v
   |                       |         |                     HCP-12 notifier
   |                       +----+----+                         |
   |                            v                              v
   |                          HCP-5                         HCP-13 center
   |                     version/publish
   |                            |
   +-------------+--------------+
                 v
               HCP-6 app/use-case binding
                 |
                 v
               HCP-7 preset exposure
                 |
                 v
               HCP-8 persistent control-plane store
                 |
                 v
               HCP-9 deterministic resolver
                 |
          |------+-------------------|
          v                          v
       HCP-17                     HCP-19
     activation                 use-case discovery
          |                          |
          v                          v
       HCP-18                     HCP-20
     residency                  preset discovery
          |                          |
          +-----------+--------------+
                      v
                    HCP-21
                Binder activation

Independent observability lane after HCP-0:

HCP-14 session contract -> HCP-15 Room history
                        -> HCP-16 runtime instrumentation

Harness UI can proceed against fakes once contracts exist:
HCP-22 Applications
HCP-23 Use Case Builder
HCP-24 Preset Editor
HCP-25 Decision Center / notifications
HCP-26 Sessions / inference history

Cutover:
HCP-20 + HCP-21 + UI/runtime stores
        -> HCP-27 hardcoded-binding removal + cross-repo validation
```

## Parallel execution lanes

Four lanes can begin after HCP-0 without waiting on one another:

| Lane | Initial sequence | Merge dependency |
| --- | --- | --- |
| Control plane | HCP-1 -> HCP-2 -> HCP-3/HCP-4 -> HCP-5 -> HCP-6 -> HCP-7 -> HCP-8 -> HCP-9 | HCP-0 |
| Decision/notifications | HCP-10 -> HCP-11 -> HCP-12 -> HCP-13 | HCP-0; integration actions depend on HCP-8/9 |
| Unified observability | HCP-14 -> HCP-15 and HCP-16 | HCP-0 |
| Residency | design/tests for HCP-17/18, then runtime integration | HCP-9 before final activation binding |

Consumer/Binder work starts when HCP-9 and HCP-17 define stable semantics. Harness UI may start earlier against injected repositories/fakes and must not own policy.

---

## HCP-0 — Architecture freeze

State: **DONE on integration of this plan/ADR**

Freeze these decisions in ADR 0015:

- Harness owns application registration, use-case definitions, preset definitions, exact execution binding, residency and full host diagnostics.
- Consumers choose only host-assigned use cases and host-published presets.
- Fast/Balanced/Quality are optional suggested templates, not fixed protocol values.
- Users may create custom presets and publish/expose them to selected consumers.
- Configuration revisions are immutable execution identity for already-started activations/sessions.
- every host-executed inference uses the same telemetry path;
- activation leases, not Binder presence or session existence alone, own normal residency protection;
- actionable decisions have one structured source of truth and may be projected to Android notifications.

Exit gate: ADR accepted, roadmap integrated, existing Consumer API/shared-runtime docs point to this owner.

## HCP-1 — Persistent application registry

Dependencies: HCP-0.

Deliverables:

- `RegisteredApplication` domain model with stable Harness application ID, package, signer identity, display name, enabled state, first/last seen timestamps and registration state;
- registration states at least `PENDING`, `AUTHORIZED`, `DISABLED`, `SIGNATURE_CHANGED` and `UNINSTALLED`/unavailable when observable;
- caller-authentication adapter maps Binder caller identity into the registry without trusting request-supplied identity;
- unknown same-signer callers may be recorded as pending but cannot execute inference until authorized/configured;
- bounded query API for Harness UI;
- deterministic tests for first sighting, repeat sighting, disabled caller, signer change and concurrent registration.

Exit gate: caller identity is no longer represented only by hardcoded application branches; registry behavior is deterministic and does not weaken same-signer authorization.

## HCP-2 — Harness-managed use-case definitions

Dependencies: HCP-0.

Deliverables:

- persistent `UseCaseDefinition` domain model with ID, display name, description, requirements, lifecycle state and revision;
- requirements cover supported input/output contract, output constraint, reasoning policy, session policy, minimum context/limits and compatible execution characteristics without naming a concrete artifact;
- state model at least `DRAFT`, `ACTIVE`, `DISABLED`;
- create/update/disable operations owned by Harness control plane;
- no consumer application may create or mutate use cases through the public Consumer API;
- migration path for the existing `document-pii-detection` use case into seeded Harness configuration without retaining RedactGuard-specific runtime branches.

Exit gate: a use case can be created and queried entirely through Harness-owned domain/repository APIs.

## HCP-3 — Suggested preset templates

Dependencies: HCP-2.
Can run in parallel with HCP-4.

Deliverables:

- deterministic suggestion service taking use-case requirements, installed compatible model profiles and supported runtime capabilities;
- suggested templates may include names such as Fast, Balanced and Quality when justified by available candidates;
- each suggestion explains the optimization intent and why the candidate is compatible;
- suggestions do not become active/published automatically;
- no network or external LLM dependency is required for v1 suggestions;
- tests cover no compatible model, one candidate, multiple candidates and deterministic ordering.

Exit gate: Harness can propose bounded candidate presets without turning suggestion labels into public protocol constants.

## HCP-4 — Custom preset domain

Dependencies: HCP-2.
Can run in parallel with HCP-3.

Deliverables:

- `UseCasePreset` with stable ID, use-case ID, display metadata, creation source (`SUGGESTED`/`CUSTOM`), state, revision and visibility;
- Harness-only execution configuration includes exact model profile or resolver policy, generation profile, context/limits, cache and residency policy;
- consumer-visible projection excludes model/artifact/digest/path and raw backend tuning;
- states at least `DRAFT`, `PUBLISHED`, `DEPRECATED`, `DISABLED`;
- custom preset validation fails before publication when execution requirements are impossible or unsafe.

Exit gate: user-created presets are first-class domain objects distinct from suggested templates.

## HCP-5 — Preset revision and publishing semantics

Dependencies: HCP-3 and HCP-4.

Deliverables:

- immutable published revision identity;
- editing a published preset creates a new revision rather than mutating execution identity in place;
- existing activation/session retains its preset revision;
- publication/deprecation/disable transitions are explicit and validated;
- stale client capability/preset revisions fail with typed incompatibility rather than silently upgrading;
- tests prove revision pinning during concurrent configuration changes.

Exit gate: historical execution can always identify the exact preset revision used.

## HCP-6 — Application/use-case binding

Dependencies: HCP-1 and HCP-2.

Deliverables:

- `ApplicationUseCaseBinding` with binding ID, application ID, use-case ID, enabled/default state and revision;
- assignment/removal/disable operations in Harness only;
- an authorized application with no active binding returns an explicit `USE_CASE_NOT_BOUND`/equivalent host-policy failure with evidence;
- existing `document-pii-detection` authorization is migrated through seeded/persisted data rather than code branches.

Exit gate: application -> use-case association is data-driven and revisioned.

## HCP-7 — Per-application preset exposure

Dependencies: HCP-4, HCP-5 and HCP-6.

Deliverables:

- explicit relation controlling which published presets of a bound use case are visible to a given application;
- exactly one default may be configured when presets are exposed; zero exposed presets leaves the use case unavailable/configuration-required;
- unpublished/disabled/deprecated policy is explicit;
- consumer capability projection returns only exposed published metadata;
- tests cover custom preset publication, withdrawal, default changes and stale capability revisions.

Exit gate: Harness can expose Fast/Quality/custom combinations independently per consumer without changing consumer code.

## HCP-8 — Persistent control-plane store

Dependencies: HCP-1 through HCP-7 domain contracts.

Deliverables:

- dedicated persistent adapter for applications, use cases, preset revisions, bindings, exposure and residency configuration;
- schema/migrations are isolated from runtime telemetry storage ownership even if both use Room;
- transactional revision/update semantics;
- restart reconciliation and invalid-reference detection;
- bounded queries and no sensitive prompt/document/output data;
- migration tests and deterministic fake/in-memory implementation for unit/UI tests.

Exit gate: all control-plane configuration survives process death and has one repository owner.

## HCP-9 — Deterministic execution resolver

Dependencies: HCP-8 plus installed model/profile contracts.

Deliverables:

Resolve `(applicationId, useCaseId, presetId/revision)` into an immutable `ResolvedExecutionTarget` containing at least:

- application/use-case/binding/preset revision identity;
- exact compatible model profile/digest;
- effective generation profile;
- effective residency policy;
- privacy-safe resolution evidence.

Typed failures must distinguish at least application unknown/disabled, use case not bound, preset not exposed, stale revision, model missing and model incompatible. Candidate rejection reasons remain Harness diagnostic data.

Exit gate: no consumer execution depends on the global phone `selectedModel` or an application-specific resolver branch.

## HCP-10 — Decision event contract

Dependencies: HCP-0.
Can run independently of control-plane persistence.

Deliverables:

- stable `HarnessDecisionEvent`/equivalent with event ID, type/severity, source code, optional app/use-case/preset/binding identity, timestamps, resolution state, dedupe key and action target;
- categories at least `ACTION_REQUIRED`, `WARNING`, `INFORMATION`, `COMPLETED`;
- bounded details and privacy-safe evidence fields;
- decision events are distinct from raw logs and from Android notifications.

Exit gate: actionable operational state has one typed domain representation.

## HCP-11 — Decision rules and deduplication

Dependencies: HCP-10.

Deliverables:

Rules generate/resolve events for at least:

- new/pending application;
- application missing configuration;
- binding/use-case/preset unavailable;
- bound model missing or incompatible;
- preset broken after model removal/change;
- active-model conflict with another requested target;
- critical memory-pressure eviction/revocation;
- signer change/security re-authorization.

Single inference failures remain visible in session/history but do not create notification spam by default. Repeated equivalent states dedupe into one unresolved decision.

Exit gate: deterministic tests cover event creation, dedupe, resolution and re-open after recurrence.

## HCP-12 — Android notification adapter

Dependencies: HCP-10 and HCP-11.

Deliverables:

- notification channels with user-appropriate importance;
- only configured actionable/warning events are projected to system notifications;
- notification permission/state is handled explicitly on supported Android versions;
- tapping a notification deep-links to the exact Harness decision/configuration surface;
- notification dismissal does not silently resolve the underlying decision;
- no sensitive prompt/output/model path in notification text;
- bounded notification IDs/deduplication.

Exit gate: actionable decisions can reach the user without creating a second source of truth.

## HCP-13 — Persistent Decision Center

Dependencies: HCP-10/11 and persistent adapter chosen for decision state.

Deliverables:

- unresolved/resolved decision history with bounded retention;
- query by severity/application/use case/status;
- explicit actions such as configure app, repair preset, inspect conflict or acknowledge information;
- decision state survives process death;
- UI source is the decision repository, never NotificationManager state.

Exit gate: Harness has an operational inbox that can explain what needs attention and why.

## HCP-14 — Session observability contract

Dependencies: HCP-0.
Can run in parallel with HCP-1/2/10.

Deliverables:

- `InferenceSessionRecord` containing session ID, application ID, use-case ID, preset/binding revision identity, model digest, session kind, created/closed timestamps, status and close reason;
- generation-run records gain session identity and relevant binding/preset revision fields;
- lifecycle statuses/close reasons cover normal close, cancellation, client disconnect, host shutdown/restart, model revocation/memory pressure and runtime failure;
- no prompt or generated content persistence.

Exit gate: every generation record can be joined to its owning session and execution policy identity.

## HCP-15 — Persistent Harness runtime history

Dependencies: HCP-14.

Deliverables:

- production Harness composition uses bounded Room-backed telemetry rather than process-only `InMemoryTelemetryRepository`;
- Room schema stores sessions and existing generation/log/resource data with tested migrations;
- active rows left by process death reconcile to an explicit abandoned/restarted terminal state;
- queries support application, use case, preset, model, status and date filters;
- test implementations remain lightweight/in-memory.

Exit gate: internal/external session and generation history survives Harness restart.

## HCP-16 — Runtime-first unified instrumentation

Dependencies: HCP-14. HCP-15 may proceed in parallel.

Deliverables:

- session creation/close and all generation lifecycle instrumentation occurs at the shared runtime/orchestration boundary rather than in Playground or Binder UI adapters;
- Harness Playground, device tests and Binder consumers produce the same record types automatically;
- rejected requests preserve enough application/use-case/request evidence to diagnose them without fabricating a session;
- telemetry failure remains best-effort and cannot corrupt inference.

Exit gate: there is no execution path through Harness runtime that bypasses host telemetry.

## HCP-17 — Use-case activation lease

Dependencies: HCP-9 for final integration; contract/test design may begin after HCP-0.

Deliverables:

- activation identity distinct from session identity;
- activation pins application/use-case/preset/binding revision/model identity;
- one activation may create multiple stateless sessions over time;
- client/process death and explicit deactivate release owned activation;
- activation ownership is isolated per authenticated connection/application.

Exit gate: product-level "local AI active" has an explicit host resource owner instead of being inferred from Binder presence.

## HCP-18 — Lease-aware model residency

Dependencies: HCP-17 and existing model residency lifecycle.

Deliverables:

- normal unload is forbidden while any active lease owns the resident model;
- after the last lease releases, effective warm-retention policy starts;
- warm retention comes from resolved Harness configuration, not a single service hardcoded TTL;
- multiple compatible leases may share one resident model;
- with the one-resident-model invariant, a conflicting model activation fails explicitly while another model has protected leases; no silent preemption;
- critical pressure may revoke/evict only according to explicit policy and must emit typed failure/decision/telemetry reason;
- tests cover session gaps, multiple leases, Binder death, TTL, pressure and manual model actions.

Exit gate: a consumer can remain active across sessions without the model disappearing because a Binder/session idle timer fired.

## HCP-19 — Assigned use-case discovery

Dependencies: HCP-6/7/9.

Deliverables:

- Consumer API exposes only use cases assigned to the authenticated caller;
- safe metadata includes ID/display name/description/default marker/revision as needed;
- callers cannot enumerate another application's bindings;
- old v1.1 single-use-case clients remain supported during the compatibility window.

Exit gate: a consumer need not compile a Harness-owned use-case definition into product code when the host can advertise its assignment.

## HCP-20 — Published preset discovery

Dependencies: HCP-7/9.

Deliverables:

- capability projection supports one or many exposed presets including user-created presets;
- consumer metadata excludes exact model/artifact/runtime internals;
- explicit default preset plus revision;
- capability refresh handles publication/withdrawal deterministically;
- stale selections fail with typed retry/refresh guidance.

Exit gate: adding a custom Harness preset can make it appear in a compatible consumer without consumer release or model knowledge.

## HCP-21 — Consumer activation protocol / Binder minor evolution

Dependencies: HCP-17/18/19/20.

Deliverables:

- additive Consumer API activation/deactivation lifecycle with version/feature negotiation;
- activation binds the selected published preset revision before session creation;
- session creation references activation/prepared execution according to the final accepted public surface;
- Binder/client death cleanup is deterministic and idempotent;
- existing v1.1 behavior remains compatible for the supported migration window;
- packaged AAR fixture and host/client contract tests cover old/new combinations.

Exit gate: external consumers can own product-level activation without receiving model identity.

## HCP-22 — Harness Applications UI

Dependencies: HCP-1/6 contracts; may start against fakes before persistence integration.

Deliverables:

- application list showing authorization/configuration/connection/attention state;
- application detail with assigned use cases, active leases/sessions and relevant decisions;
- pending application flow with clear authorize/configure action;
- no runtime policy duplicated in Compose.

Exit gate: binding decisions are discoverable and editable in Harness rather than hidden in code.

## HCP-23 — Harness Use Case Builder

Dependencies: HCP-2/3/5 and repository fakes.

Deliverables:

- create/edit/disable use cases;
- progressive-disclosure editor for requirements;
- suggested preset generation/list with compatibility explanation;
- clear draft/active status and revision identity;
- invalid configurations show actionable reasons before publication.

Exit gate: `document-pii-detection` and future use cases can be administered through Harness UI.

## HCP-24 — Harness Preset Editor

Dependencies: HCP-4/5/7/9.

Deliverables:

- create custom preset from scratch or clone a suggestion;
- edit consumer-visible name/description separately from Harness-only execution settings;
- choose automatic/specific compatible model resolution as supported by the accepted resolver design;
- configure generation/context/residency through progressive disclosure;
- publish/deprecate/disable and control exposure per application;
- show broken/incompatible state with concrete remediation.

Exit gate: the user can create a custom preset in Harness and expose it to a selected consumer.

## HCP-25 — Decision Center / notification UI

Dependencies: HCP-12/13.

Deliverables:

- Overview "needs attention" summary;
- decision list/detail with severity, evidence and exact next action;
- deep-link targets from Android notifications;
- notification settings/preferences without hiding unresolved in-app decisions;
- accessibility and empty/loading/error states.

Exit gate: operational decisions are actionable without reading raw logs.

## HCP-26 — Unified Sessions and Inferences UI

Dependencies: HCP-15/16.

Deliverables:

- sessions list across Harness internal and external applications;
- filters for application/use case/preset/model/status/date;
- session detail with lifecycle, revisions, model identity, metrics and failure/close reason;
- child inference timeline using existing generation records;
- prompts/output/document content never displayed from persisted telemetry because they are not stored.

Exit gate: Harness can answer which app executed what use case/preset, when, with which model/configuration identity, performance and failure reason.

## HCP-27 — Hardcoded-binding removal and full cutover

Dependencies: HCP-9, HCP-18, HCP-20/21, Harness admin UI, RedactGuard consumer adaptation and migration data.

Deliverables:

- remove RedactGuard/OMBRA-specific application -> model/preset branches from runtime composition;
- consumer execution no longer depends on global selected model;
- remove the consumer assumption of exactly one preset;
- migrate existing RedactGuard `document-pii-detection` configuration into persistent seed/migration data;
- validate model removal, preset breakage, signer changes, reconnect and process restart;
- update durable docs and delete/archive completed temporary plan material per repository policy.

Exit gate: runtime and UI contain no hardcoded consumer binding and all supported consumer execution is control-plane resolved.

---

## Required final validation matrix

The workstream is not complete until deterministic and representative device evidence covers at least:

| Scenario | Required result |
| --- | --- |
| Harness Playground inference | session + run persisted and visible |
| RedactGuard inference | external session + run persisted and visible |
| Harness restart | prior history remains; abandoned active rows reconciled |
| new same-signer consumer | pending/configuration decision, no unauthorized inference |
| binding absent | explicit actionable binding failure |
| one published preset | consumer may use default without selector |
| multiple published presets | consumer can select host-published choice |
| custom preset publication | appears to authorized consumer after capability refresh |
| custom preset withdrawal | stale choice fails/refreshes deterministically |
| preset edited during active session | active execution retains old revision |
| activation active, zero sessions | model remains resident |
| last activation released | configured warm retention starts |
| warm retention expires | model unloads with explicit reason |
| two apps, same model | compatible leases coexist |
| conflicting model with active lease | explicit conflict; no silent preemption |
| Binder/process death | sessions/requests/activation cleaned idempotently |
| critical memory pressure | explicit policy outcome and evidence/decision event |
| bound model removed | active ownership blocks unsafe removal; inactive binding becomes broken/actionable |
| prompt/output privacy | no sensitive content in Room/log/decision/notification evidence |

Physical evidence remains required for cross-APK lifecycle/residency claims and must record exact host/client/runtime/model/device identity.

## Recommended PR slices

Use one coherent deliverable per branch/PR. Parallel branches are encouraged only when ownership is disjoint and dependencies above are explicit.

| Slice | Scope | Depends on |
| --- | --- | --- |
| HCP-A | HCP-1/2 foundational domain | HCP-0 |
| HCP-B | HCP-3/4/5 preset semantics | HCP-A use-case contract |
| HCP-C | HCP-6/7/8 binding + persistence | HCP-A/B |
| HCP-D | HCP-9 resolver | HCP-C |
| HCP-E | HCP-10/11 decision domain | HCP-0 |
| HCP-F | HCP-12/13 notification/decision persistence | HCP-E |
| HCP-G | HCP-14 session/run observability contract | HCP-0 |
| HCP-H | HCP-15 persistent telemetry | HCP-G |
| HCP-I | HCP-16 unified runtime instrumentation | HCP-G |
| HCP-J | HCP-17/18 activation/residency | HCP-D |
| HCP-K | HCP-19/20 discovery | HCP-C/D |
| HCP-L | HCP-21 Binder/API evolution | HCP-J/K |
| HCP-M | HCP-22/23/24 admin UI | corresponding control-plane contracts |
| HCP-N | HCP-25 decision UI | HCP-F |
| HCP-O | HCP-26 history UI | HCP-H/I |
| HCP-P | HCP-27 cross-repo cutover/evidence | all prerequisites |

## State rule

`PLANNED` means no integrated behavior may be assumed. `IN PROGRESS` begins when implementation work starts on the owning branch. `DONE` requires the stated exit gate integrated into `dev` with the narrowest applicable deterministic validation green. Physical/device evidence is never inferred from compilation, JVM tests or emulator-only execution.
