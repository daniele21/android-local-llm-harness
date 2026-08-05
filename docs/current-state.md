# Current Repository State

Last updated: 2026-08-05

This document is the active integration and recovery ledger for the repository. It complements the historical detail in `docs/roadmap.md` and must be updated whenever a merged pull request changes the next operational block.

## Canonical line

`main` is the only canonical integrated implementation line.

Integrated head before the next recovery block:

```text
be8a2c61924ea955b6a844f6700754ab2fefb50e
Connect model catalog download and installation UI (#49)
```

Historical implementation, staging and sandbox branches are audit references only. New work must start from current `main` unless a pull request explicitly documents a temporary stacked dependency.

## Completed repository cleanup and infrastructure

- PR #44 aligned README and established this state ledger.
- PR #45 refreshed Android Gradle Plugin 9.3.1 from current `main` and passed complete Android validation.
- PR #47 refreshed all workflows to `actions/checkout@v7`, split the model-distribution gate into attributable phases and passed cumulative validation.
- PR #48 added the verified-download installation boundary, opaque verified handles, metadata-only GGUF inspection and non-destructive post-import failure handling.
- PR #49 connected the phone Models UI to catalog, secure download and verified installation, and added durable path-free installed metadata.
- PRs #20, #3 and #4 were closed with explicit superseded/obsolete disposition notes.
- Issue #46 tracks branch protection and the required `Repository validation` repository setting.

## Legacy feature branches requiring selective recovery

PR #33 and PR #34 must not be merged directly. Both are based on the pre-Compose console line and have diverged materially from current `main`.

### PR #33 — benchmark history

Potentially unique behavior to recover on a fresh branch:

- retained multi-capture benchmark history;
- active-baseline versus immutable-history semantics;
- non-destructive Room migration for retained captures;
- historical metric presentation not already covered by the connected Compose application.

Do not restore the old standalone-console composition or duplicate existing benchmark logic.

### PR #34 — model management

Potentially unique behavior to recover on a fresh branch:

- explicit model verification action;
- confirmation before removal;
- blocking removal of the currently loaded model;
- reusable privacy-safe model-management controls;
- staged-file cleanup and operation-state tests not already present in the connected app.

Do not create a parallel model store or reconnect the old standalone-console sandbox as the product path.

## Current functional boundary

### Integrated before the connected-distribution slice

- explicit application/use-case to GGUF profile resolution;
- content-addressed installed-model store;
- local `llama.cpp` load, context, generation, streaming and cancellation;
- telemetry, logs, health, resources, benchmarks and cache repair;
- connected Compose Playground and Diagnostics surfaces;
- administrator-managed catalog contracts, persistence and compatibility;
- secure remote transfer to a verified app-private holding area;
- explicit verified-download installation into `ModelStore` with post-import integrity verification.

### Integrated connected distribution

The phone-test Models surface now connects the existing distribution boundaries:

```text
administrator-curated catalog
  -> catalog validation
  -> target and device compatibility
  -> explicit Download
  -> progress or cooperative cancellation
  -> opaque verified download
  -> explicit Install
  -> verified ModelStore import
  -> durable path-free catalog/profile metadata
  -> installed state
  -> explicit Use in Playground
  -> integrity verification
  -> selected model for local inference
```

The integrated implementation includes:

- application dependencies on `models:model-catalog`, `models:model-download` and `models:model-install`;
- Android `INTERNET` permission for secure remote model transfer;
- device-aware catalog filtering for the phone-test application/use-case target;
- Compose model cards for compatible, incompatible, downloading, verified, installing, installed, cancelled and failed states;
- immediate publication of `DOWNLOADING` and `INSTALLING` before worker execution;
- download byte progress, percentage and cooperative cancellation;
- explicit installation through `VerifiedModelInstaller`, without implicit runtime activation;
- schema-versioned, atomic persistence of catalog release, target and application-profile metadata by digest;
- startup and refresh reconciliation between persisted metadata and the shared `ModelStore`;
- explicit, integrity-checked selection of an installed catalog model for the existing Playground;
- deterministic tests for metadata persistence, stale-record cleanup, progress, cancellation and installation;
- an operational document covering the connected phone distribution flow.

No download URL, signed URL or filesystem path is persisted in installed-model metadata.

## Ordered implementation plan

### Block 1 — repository state alignment

Status: **DONE through PR #44**.

### Block 2 — refreshed infrastructure updates

Status: **DONE through PRs #45 and #47**.

### Block 3 — verified-download installation boundary

Status: **DONE through PR #48**.

### Block 4 — connected catalog and installation UI

Status: **DONE through PR #49**.

Validated behavior includes:

- catalog loading for the phone-test target;
- compatible and incompatible release presentation;
- explicit download, progress and cancellation;
- verified-ready-to-install state;
- explicit installation and installed-state refresh;
- no implicit binding, runtime load or inference activation;
- green Spotless, Detekt, unit tests, Android Lint, packaging and cumulative repository validation.

### Block 5 — durable installed-model metadata

Status: **DONE through PR #49**.

The persisted relationship is path-free and includes installed digest, catalog release, application-reviewed profile and target. Persistence does not activate or alter bindings implicitly.

### Block 6 — selective benchmark-history recovery

Status: **NEXT**.

Recover only unique retained-history behavior from PR #33 on a fresh branch from current `main`.

### Block 7 — selective model-management recovery

Recover unique verification, confirmation and loaded-model protection behavior from PR #34 on a fresh branch from the then-current `main`.

### Block 8 — product and hardware completion

- complete ViewModel/UDF and Navigation Compose detail routes;
- add Compose UI, screenshot, accessibility and responsive tests;
- validate catalog download and installation on representative physical devices;
- execute the complete real-GGUF production-readiness gate;
- record privacy-safe release evidence.

## Deferred after connected distribution

- remote administrator catalog synchronization and trust-policy wiring in the phone app;
- transactional `ModelStore` creation provenance required for safe automatic rollback;
- durable restoration of a verified-ready-to-install UI state without an explicit deduplicated Download action;
- explicit application/use-case binding after installation;
- cancellation during synchronous `ModelStore.import()`;
- WorkManager or foreground-service execution for long-running distribution operations;
- parallel model downloads or installations;
- physical-device remote-download, installation and inference evidence.

## Merge discipline

Each block follows:

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

Issue #46 tracks operational hardening outside the code tree:

- protect `main`;
- require pull requests;
- require the stable `Repository validation` check;
- block force pushes and deletion of `main`;
- enable automatic deletion of merged feature branches where appropriate.
