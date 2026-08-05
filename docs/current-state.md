# Current Repository State

Last updated: 2026-08-05

This document is the active integration and recovery ledger for the repository. It complements the historical detail in `docs/roadmap.md` and must be updated whenever a merged pull request changes the next operational block.

## Canonical line

`main` is the only canonical integrated implementation line.

Integrated head before this installation slice:

```text
04045a1226d90ea7ee25ad7adc12dd1fc71e6307
Update GitHub checkout action to v7 (#47)
```

Historical implementation, staging and sandbox branches are audit references only. New work must start from current `main` unless a pull request explicitly documents a temporary stacked dependency.

## Completed repository cleanup and infrastructure

- PR #44 aligned README and established this state ledger.
- PR #45 refreshed Android Gradle Plugin 9.3.1 from current `main` and passed complete Android validation.
- PR #47 refreshed all workflows to `actions/checkout@v7`, split the model-distribution gate into attributable phases and passed cumulative validation.
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

### Integrated before this slice

- explicit application/use-case to GGUF profile resolution;
- content-addressed installed-model store;
- local `llama.cpp` load, context, generation, streaming and cancellation;
- telemetry, logs, health, resources, benchmarks and cache repair;
- connected Compose Playground and Diagnostics surfaces;
- administrator-managed catalog contracts, persistence and compatibility;
- secure remote transfer to a verified app-private holding area.

### Implemented by the verified-installation slice

The remote distribution path is extended to:

```text
CatalogGgufArtifact
  -> secure verified transfer
  -> opaque VerifiedDownloadHandle
  -> exact catalog/profile/target validation
  -> controlled staging copy with digest revalidation
  -> metadata-only GGUF inspection
  -> architecture and available quantization validation
  -> ModelStore import
  -> post-import integrity verification
  -> non-destructive failure when verification is invalid or unavailable
  -> path-free InstalledModelDescriptor
```

The implementation adds:

- `VerifiedDownloadAccess` in `models/model-download`, which never exposes the verified backing path;
- `models/model-install`, a UI-independent installation coordinator;
- `LlamaCppGgufArtifactInspector`, an adapter over the existing metadata-only JNI bridge;
- deterministic tests for opaque access, tampering, profile mismatch, revoked releases, inspection mismatch, import/verification failure, non-destructive failure handling and cleanup;
- dedicated and cumulative CI coverage for catalog, download, installation and the backend adapter;
- ADR 0007 and the installation operations document.

Installation still does not activate a binding, load the runtime or start inference.

## Ordered implementation plan

### Block 1 — repository state alignment

Status: **DONE through PR #44**.

### Block 2 — refreshed infrastructure updates

Status: **DONE through PRs #45 and #47**.

### Block 3 — verified-download installation boundary

Status: **IMPLEMENTED; awaiting pull-request CI and merge**.

Remaining acceptance before completion:

- pass Spotless, Detekt, module unit tests and Android Lint;
- pass cumulative repository validation and native packaging checks;
- merge only after all public contracts, tests and documentation are consistent.

### Block 4 — connected catalog and installation UI

Status: **NEXT after Block 3 is merged**.

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

### Block 5 — durable installed-model metadata

Persist the path-free relationship among installed digest, catalog release, application-reviewed profile and target. Persistence must not activate or alter bindings implicitly.

### Block 6 — selective benchmark-history recovery

Recover only unique retained-history behavior from PR #33 on a fresh branch from the then-current `main`.

### Block 7 — selective model-management recovery

Recover unique verification, confirmation and loaded-model protection behavior from PR #34 on a fresh branch from the then-current `main`.

### Block 8 — product and hardware completion

- complete ViewModel/UDF and Navigation Compose detail routes;
- add Compose UI, screenshot, accessibility and responsive tests;
- validate catalog download and installation on representative physical devices;
- execute the complete real-GGUF production-readiness gate;
- record privacy-safe release evidence.

## Deferred from the installation slice

- transactional `ModelStore` creation provenance required for safe automatic rollback;
- durable installed-model metadata persistence;
- catalog/download/install Compose UI;
- explicit application/use-case binding after installation;
- cancellation during synchronous `ModelStore.import()`;
- WorkManager or foreground-service execution;
- physical-device remote-download and installation evidence.

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
