# Current Repository State

Last updated: 2026-08-05

This document is the active integration and recovery ledger for the repository. It complements the historical detail in `docs/roadmap.md` and must be updated whenever a merged pull request changes the next operational block.

## Canonical line

`main` is the only canonical integrated implementation line.

Current integrated head at the time of this update:

```text
c25b15e56389a84a5c474cbcff414d019267c534
Harden secure model transfer boundary (#43)
```

The cumulative `Validate` workflow and Android artifact packaging workflow passed for this head.

Historical implementation, staging and sandbox branches are audit references only. New work must start from current `main` unless a pull request explicitly documents a temporary stacked dependency.

## Integrated pull-request sequence

The current implementation includes the following major merged lines:

- PR #13 — Phase 1 functional embedded runtime;
- PRs #21, #23–#28 — persistent telemetry, health, sanity, cache, resources, benchmarks and emulator preflight;
- PRs #29, #32, #36 and #37 — Play-installable physical-device app, signing and connected manual inference;
- PR #31 — console observability, controls and cache repair;
- PR #35 — capability-driven manual console playground;
- PRs #39 and #40 — canonical Harness UX/UI plan and connected Compose implementation;
- PRs #41–#43 — administrator-managed catalog, persistence, secure download and verified-transfer hardening.

## Open pull-request disposition

### Closed as obsolete

- PR #20: superseded because current `main` already uses `gradle/actions/setup-gradle@v6` with the selected cache configuration.
- PR #3: obsolete Dependabot Android Gradle Plugin branch based on an old `main`; recreate from current `main`.
- PR #4: obsolete Dependabot `actions/checkout` branch based on an old `main`; recreate from current `main`.

### Legacy feature branches requiring selective recovery

PR #33 and PR #34 must not be merged directly.

Both are based on the pre-Compose console line and have diverged materially from current `main`. Their unique responsibilities must be recovered through new focused branches from current `main`.

#### PR #33 — benchmark history

Potentially unique behavior to recover:

- retained multi-capture benchmark history;
- explicit active-baseline versus immutable-history semantics;
- non-destructive Room schema migration for retained captures;
- a shared comparison evaluator when not already present in the connected implementation;
- historical metric presentation not already covered by `apps/local-llm-phone-test`.

Recovery must not restore the old standalone-console composition or duplicate benchmark logic already present in the connected Compose application.

#### PR #34 — model management

Potentially unique behavior to recover:

- explicit model verification action;
- confirmation before model removal;
- blocking removal of the currently loaded model at both presentation and control boundaries;
- reusable, privacy-safe model-management control contracts;
- deterministic staged-file cleanup and operation-state tests not already present in the connected application.

Recovery must not create a parallel model store, duplicate current SAF import behavior or reconnect the old standalone-console sandbox as the product path.

## Current functional boundary

### Complete

- explicit application/use-case to GGUF profile resolution;
- content-addressed installed-model store;
- GGUF inspection and integrity verification;
- local `llama.cpp` load, context, generation, streaming and cancellation;
- embedded runtime lifecycle and single-decode scheduling;
- telemetry, logs, health, resources, benchmarks and cache repair;
- connected Compose Playground and Diagnostics surfaces;
- administrator-managed catalog contracts, persistence and compatibility;
- secure remote transfer to a verified app-private holding area.

### Not complete

The remote distribution path currently stops at:

```text
CatalogGgufArtifact
  -> secure transfer
  -> VerifiedDownloadHandle
```

It must be extended explicitly to:

```text
VerifiedDownloadHandle
  -> GGUF structural inspection
  -> application-owned profile validation
  -> ModelStore import
  -> post-import integrity verification
  -> installed-model metadata
  -> optional explicit application/use-case binding
```

Installation must not activate a binding or load the runtime implicitly.

## Ordered implementation plan

### Block 1 — repository state alignment

- close obsolete PRs #20, #3 and #4 with clear disposition notes;
- make README reflect the implementation merged through PR #43;
- establish this active state ledger;
- keep dependency replacement work separate from feature work.

### Block 2 — refreshed infrastructure updates

Create separate focused pull requests from current `main` for:

1. Android Gradle Plugin 9.3.0 to 9.3.1;
2. `actions/checkout@v6` to `actions/checkout@v7`.

Each change must pass the complete repository validation applicable to validation-infrastructure changes before merge.

### Block 3 — verified-download installation boundary

Introduce a UI-independent installation module or boundary that:

- accepts only an opaque `VerifiedDownloadHandle` plus catalog/profile context;
- revalidates handle ownership, size and digest before use;
- performs metadata-only GGUF inspection before import;
- rejects incompatible architecture, quantization or profile mapping;
- imports through the existing `ModelStore` contract;
- performs post-import verification;
- returns an installed-model result without binding or loading it;
- consumes or safely retains the verified holding artifact according to an explicit policy;
- emits privacy-safe progress and typed failures;
- is deterministic and testable without Android UI.

### Block 4 — connected catalog and installation UI

Extend `apps/local-llm-phone-test` with explicit states for:

```text
catalog unavailable
catalog stale or expired
release incompatible
ready to download
downloading
verified, awaiting installation
installing
installed
failed or cancelled
```

The user must explicitly select, download and install. Runtime activation remains a separate action or existing prepare flow.

### Block 5 — selective benchmark-history recovery

Recover the unique retained-history behavior from PR #33 on a fresh branch from the then-current `main`. Prefer the connected Compose Diagnostics implementation and avoid old-console duplication.

### Block 6 — selective model-management recovery

Recover unique verification, confirmation and loaded-model protection behavior from PR #34 on a fresh branch from the then-current `main`.

### Block 7 — product and hardware completion

- complete ViewModel/UDF and Navigation Compose detail routes;
- add Compose UI, screenshot, accessibility and responsive tests;
- validate catalog download and installation on representative physical devices;
- execute the complete real-GGUF production-readiness gate;
- record privacy-safe release evidence.

## Merge discipline

Each block must follow:

```text
fresh branch from current main
  -> focused implementation
  -> deterministic tests
  -> documentation and this ledger updated
  -> complete CI
  -> merge
  -> next block starts from refreshed main
```

Do not merge legacy stacked PRs merely because GitHub reports them as mergeable. Mergeability is not evidence of architectural currency or absence of duplication.

## Repository administration still required

Repository settings currently require manual hardening outside the code tree:

- protect `main`;
- require pull requests for changes;
- require the stable `Repository validation` check;
- block force pushes and deletion of `main`;
- enable automatic deletion of merged feature branches where appropriate.

These settings are operational prerequisites but do not replace the repository Definition of Done.
