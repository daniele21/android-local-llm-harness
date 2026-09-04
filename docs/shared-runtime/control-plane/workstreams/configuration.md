# Host Control Plane configuration workstream

Status: active
Document type: feature-specification
Owner: shared-runtime-control-plane
Canonical scope: shared-runtime.control-plane.configuration
Read when: implementing Harness applications, use cases, presets, bindings, control-plane persistence, resolver or their administration UI
Last reviewed: 2026-08-18

This workstream owns HCP-1 through HCP-9 plus the Harness administration surfaces HCP-22 through HCP-24. The durable responsibility split is ADR 0015; sequencing and global milestone state stay in [`../roadmap.md`](../roadmap.md).

## HCP-1 — Persistent application registry

Dependencies: HCP-0.

Implement a Harness-owned `RegisteredApplication` model with stable application ID, package, signer identity, display name, lifecycle state and first/last-seen timestamps. Required states include pending, authorized, disabled, signer-changed and unavailable/uninstalled when observable. Binder caller identity remains host-derived; request-supplied identity is never trusted.

Unknown same-signer callers may be recorded as pending but cannot execute until authorized/configured. Provide bounded queries and deterministic first-sighting, repeat-sighting, disabled, signer-change and concurrency coverage.

Exit gate: caller identity/configuration no longer exists only as hardcoded application branches.

## HCP-2 — Harness-managed use-case definitions

Dependencies: HCP-0.

Make `UseCaseDefinition` a Harness-owned revisioned object with display metadata, input/output requirements, output-constraint policy, reasoning policy, session policy, limits/minimum context and lifecycle state (`DRAFT`, `ACTIVE`, `DISABLED`). Requirements describe execution characteristics without naming an exact model artifact.

Consumers may discover assigned use cases but cannot create or mutate them. Seed/migrate the existing `document-pii-detection` definition into Harness-owned data before application-specific runtime branches are removed.

Exit gate: a use case can be created, updated, disabled and queried through Harness-owned domain/repository APIs.

## HCP-3 — Suggested preset templates

Dependencies: HCP-2. Parallel with HCP-4.

Build a deterministic local suggestion service from use-case requirements, compatible installed profiles and supported runtime capabilities. Suggested names such as Fast, Balanced or Quality are optional templates, never protocol constants. Each suggestion explains optimization intent and compatibility rationale and remains draft until explicitly accepted/published.

V1 must not require network access or an external LLM. Cover zero, one and multiple compatible candidates with deterministic ordering.

Exit gate: Harness can propose bounded preset candidates without automatically publishing them.

## HCP-4 — Custom preset domain

Dependencies: HCP-2. Parallel with HCP-3.

A `UseCasePreset` has stable ID, use-case ID, revision, consumer-visible display name/description, creation source (`SUGGESTED` or `CUSTOM`) and lifecycle state (`DRAFT`, `PUBLISHED`, `DEPRECATED`, `DISABLED`). Harness-only execution configuration contains exact model-profile or resolver policy, generation profile, context/limits, cache and residency policy.

Consumer projection excludes exact model/artifact/digest/path and unrestricted backend tuning. Publication fails when execution requirements cannot be satisfied safely.

Exit gate: user-created presets are first-class Harness objects and are distinct from suggested templates.

## HCP-5 — Revision and publishing semantics

Dependencies: HCP-3 and HCP-4.

Published revisions are immutable execution identity. Editing a published preset creates a new revision; already-started activations/sessions retain their pinned revision. Publication, deprecation and disable transitions are explicit. Stale consumer revisions fail with typed refresh/retry guidance rather than silently upgrading.

Exit gate: historical execution always identifies the exact preset revision used.

## HCP-6 — Application/use-case binding

Dependencies: HCP-1 and HCP-2.

Persist a revisioned `ApplicationUseCaseBinding` with binding ID, application ID, use-case ID and enabled/default state. Assignment, removal and disable are Harness-only operations. An authorized application with no active binding receives an explicit `USE_CASE_NOT_BOUND`-class failure with evidence.

Exit gate: application-to-use-case association is data-driven rather than compiled into the phone host.

## HCP-7 — Per-application preset exposure

Dependencies: HCP-4, HCP-5 and HCP-6.

Persist which published preset revisions are exposed through each application/use-case binding. At most one exposed preset is default. Zero exposed presets leaves the binding configuration-required/unavailable. Consumer capability projection returns only currently exposed safe metadata.

Cover custom-preset publication, withdrawal, default changes and stale capability revisions.

Exit gate: Harness can expose different combinations of suggested/custom presets to different consumers without consumer code changes.

## HCP-8 — Persistent control-plane store

Dependencies: HCP-1 through HCP-7 contracts.

Add a dedicated persistence adapter for applications, use cases/revisions, presets/revisions, bindings, exposure and residency configuration. Keep this ownership distinct from runtime telemetry storage even if both use Room. Updates affecting revisions/bindings are transactional, restart reconciliation detects invalid references, and queries are bounded.

No prompts, generated output or source-document content is stored. Provide deterministic in-memory/fake implementation for domain and UI tests plus non-destructive migration coverage.

Exit gate: all Harness control-plane configuration survives process death under one repository owner.

## HCP-9 — Deterministic execution resolver

Dependencies: HCP-8 plus installed model/profile contracts.

Resolve authenticated `(applicationId, useCaseId, presetId/revision)` into immutable host execution identity containing application/use-case/binding/preset revisions, exact compatible model profile/digest, effective generation profile, effective residency policy and privacy-safe resolution evidence.

Typed failures distinguish at least unknown/disabled app, unbound use case, unexposed preset, stale revision, missing model and incompatible model. Candidate rejection details remain Harness diagnostics. Never silently substitute another target.

Exit gate: consumer execution no longer depends on phone-global `selectedModel` or application-specific resolver branches.

## HCP-22 — Applications UI

Dependencies: HCP-1/HCP-6 contracts; UI can begin against fakes.

Provide an application list with authorization, configuration, connection and attention state. Detail shows assigned use cases, active leases/sessions and related unresolved decisions. A newly detected pending app has an explicit authorize/configure flow. Compose owns presentation only; domain policy remains injected.

## HCP-23 — Use Case Builder

Dependencies: HCP-2/HCP-3/HCP-5.

Allow create/edit/disable, requirement editing through progressive disclosure, suggestion generation with compatibility rationale, draft/active state and revision visibility. Invalid configurations must explain why before publication.

## HCP-24 — Preset Editor

Dependencies: HCP-4/HCP-5/HCP-7/HCP-9.

Allow creation from scratch or suggested template, separate consumer-visible metadata from Harness-only execution settings, choose supported automatic/specific model resolution, configure generation/context/residency under Advanced controls, publish/deprecate/disable, and expose presets per application. Broken presets show concrete remediation.

Exit gate for HCP-22/23/24: use cases, custom presets and application exposure can be fully administered from Harness UI without consumer-specific code.

## Focused validation

Run model-profile domain tests for HCP-1 through HCP-7, persistence/migration tests for HCP-8, resolver success/rejection/revision tests for HCP-9, then phone app state/accessibility tests for HCP-22 through HCP-24. Public/shared-contract fan-out requires the repository-wide Android gate. Physical evidence is not implied by these tests.
