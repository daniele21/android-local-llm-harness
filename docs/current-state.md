# Current Repository State

Last updated: 2026-08-05

This document is the active integration and recovery ledger for the repository. It complements the historical detail in `docs/roadmap.md` and must be updated whenever a merged pull request changes the next operational block.

## Canonical lines

`dev` is the canonical integration base and target for ordinary repository work. `main` is the protected stable and release-oriented line.

Current integration baseline before the governance pull request:

```text
d9404084ee79c542ca24c4c790c0e0d20d118f01
Split curated model catalog by family (#58)
```

The normal path is a focused pull request into `dev`, cumulative validation on the merged `dev` commit, then a complete `dev -> main` promotion. Historical implementation, staging and sandbox branches are audit references only. New work starts from the latest green `dev` unless it is an explicit hotfix based on `main`.

## Completed repository cleanup and infrastructure

- PR #44 aligned README and established this state ledger.
- PR #45 refreshed Android Gradle Plugin 9.3.1 from current `main` and passed complete Android validation.
- PR #47 refreshed all workflows to `actions/checkout@v7`, split the model-distribution gate into attributable phases and passed cumulative validation.
- PR #48 added the verified-download installation boundary, opaque verified handles, metadata-only GGUF inspection and non-destructive post-import failure handling.
- PR #49 connected the phone Models UI to catalog, secure download and verified installation, and added durable path-free installed metadata.
- PR #51 recovered retained benchmark history on the current connected phone-test architecture without restoring the obsolete standalone console.
- PR #55 completed the telemetry test doubles, made public-contract validation repository-wide, made technical metric formatting locale-independent and converted brand generation into a read-only reproducibility check.
- PRs #20, #3, #4 and #33 were closed with explicit superseded/obsolete disposition notes.
- Issue #46 is implemented: `main` requires an up-to-date pull request, one approval, resolved conversations and the `Repository validation` check; administrators follow the same rules, and force-push and deletion are disabled.

## Repository validation hardening

The validation scope treats changes under `core/contracts` and `observability/contracts` as repository-wide Android changes. Public contract edits can break implementations and test doubles outside the changed-file set, so they fail safe to all Android modules until a tested reverse-dependency graph replaces this policy. The telemetry contract test doubles in `runtime-core` and `health-engine` implement the current benchmark-history contract.

Brand asset generation is a read-only reproducibility check. It regenerates the committed assets and fails on a diff; it does not commit or push into a protected integration branch.

Phone-test technical metrics use locale-independent decimal formatting so repository tests and privacy-safe reports remain stable across developer and CI locales.

The repository baseline was restored on `main` through PR #55. `dev` now contains the curated-catalog expansion and the modular Detekt fix from PR #58. The active governance pull request adds cumulative `dev` validation, promotion gates, branch-target enforcement and the canonical documentation required for Harness 0.5.0. Repository-level protection for `dev` remains an administrative gate that cannot be applied from the code tree.

## Legacy feature branch requiring selective recovery

PR #34 must not be merged directly. It is based on the pre-Compose console line and has diverged materially from current `main`.

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

### Integrated retained benchmark history

The current benchmark path now separates the regression anchor from historical evidence:

```text
completed generation runs
  -> explicit baseline capture
  -> active baseline per BenchmarkKey
  -> immutable retained capture history
  -> bounded in-memory or Room persistence
  -> regression evaluation against active baseline only
  -> historical presentation in the connected Benchmarks screen
```

The integrated implementation includes:

- newest-first immutable capture history alongside the active baseline per application, use case, model digest and cold/warm load key;
- independent `maxBenchmarkBaselines` retention;
- in-memory and Room-store parity;
- non-destructive Room schema migration 3→4 that seeds existing active baseline rows into history;
- a shared comparison evaluator used by the regression health check;
- current Compose presentation of retained captures without reviving the legacy console;
- deterministic tests for active-versus-history semantics, retention, migration and UI mapping.

Viewing retained history never activates or replaces a regression baseline.

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

Status: **DONE through PR #51**.

Recovered on the current phone-test architecture: immutable retained captures, active-versus-history semantics, bounded in-memory and Room persistence, a non-destructive Room 3→4 migration, shared comparison evaluation and historical presentation. The obsolete standalone console was not restored.

### Block 7 — selective model-management recovery

Status: **IN PROGRESS through PR #53**.

PR #53 is retargeted to `dev`, contains only the focused connected-phone implementation and no longer includes temporary self-modifying workflows. Its source-level recovery compiles and its targeted controller tests pass. It remains draft until it is aligned with the final governance baseline and complete repository validation is green.

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
fresh branch from latest green dev
  -> focused implementation
  -> deterministic tests
  -> documentation and this ledger updated
  -> pull request and scoped CI
  -> squash merge into dev
  -> cumulative dev validation
  -> later complete promotion into main
```

Do not merge legacy stacked PRs merely because GitHub reports them as mergeable. Mergeability is not evidence of architectural currency or absence of duplication.

## Repository administration still required

`main` protection is implemented through issue #46. The remaining repository-level action for Harness 0.5.0 is to apply equivalent push, force-push and deletion protection to `dev`, require an up-to-date pull request and `Repository validation`, and require resolved conversations. The default branch remains `main`; feature branches are removed after merge and audit rather than through an indiscriminate global deletion rule.
