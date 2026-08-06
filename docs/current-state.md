# Current Repository State

Last updated: 2026-08-06

This document is the active integration and recovery ledger for the repository. It complements the historical detail in `docs/roadmap.md` and must be updated whenever a merged pull request changes the next operational block.

## Canonical lines

`dev` is the canonical integration base and target for ordinary repository work. `main` is the protected stable and release-oriented line.

Current remote integration baseline:

```text
2850d03
Update Harness 0.5 execution plan (#63)
```

The local integration candidate is rebased on this commit and adds the mockup-aligned phone UI
plus reproducible release/emulator tooling. Those local commits are not remote merge evidence:
they must move through a feature PR into `dev` and pass cumulative validation before this ledger
can mark their integration complete.

The normal path is a focused pull request into `dev`, cumulative validation on the merged `dev` commit, then a complete `dev -> main` promotion. Historical implementation, staging and sandbox branches are audit references only. New work starts from the latest green `dev` unless it is an explicit hotfix based on `main`.

## Completed repository cleanup and infrastructure

- PR #44 aligned README and established this state ledger.
- PR #45 refreshed Android Gradle Plugin 9.3.1 from current `main` and passed complete Android validation.
- PR #47 refreshed all workflows to `actions/checkout@v7`, split the model-distribution gate into attributable phases and passed cumulative validation.
- PR #48 added the verified-download installation boundary, opaque verified handles, metadata-only GGUF inspection and non-destructive post-import failure handling.
- PR #49 connected the phone Models UI to catalog, secure download and verified installation, and added durable path-free installed metadata.
- PR #51 recovered retained benchmark history on the current connected phone-test architecture without restoring the obsolete standalone console.
- PR #55 completed the telemetry test doubles, made public-contract validation repository-wide, made technical metric formatting locale-independent and converted brand generation into a read-only reproducibility check.
- PR #57 established the `dev` integration line, promotion gates, branch-target policy and ADR 0008.
- PR #53 recovered safe connected-phone verification and model removal, and PR #34 was closed as superseded.
- PR #60 integrated reproducible Android launcher, adaptive and monochrome brand assets.
- PR #61 completed the shared Compose design-system structure, theme modes, reusable components and baseline accessibility tests.
- PR #63 updated the Harness 0.5 execution plan to match the completed governance, recovery and brand phases.
- PRs #20, #3, #4 and #33 were closed with explicit superseded/obsolete disposition notes.
- Issue #46 is implemented: `main` requires an up-to-date pull request, one approval, resolved conversations and the `Repository validation` check; administrators follow the same rules, and force-push and deletion are disabled.

## Repository validation hardening

The validation scope treats changes under `core/contracts` and `observability/contracts` as repository-wide Android changes. Public contract edits can break implementations and test doubles outside the changed-file set, so they fail safe to all Android modules until a tested reverse-dependency graph replaces this policy. The telemetry contract test doubles in `runtime-core` and `health-engine` implement the current benchmark-history contract.

Brand asset generation is a read-only reproducibility check. It regenerates the committed assets and fails on a diff; it does not commit or push into a protected integration branch.

Phone-test technical metrics use locale-independent decimal formatting so repository tests and privacy-safe reports remain stable across developer and CI locales.

The repository baseline was restored on `main` through PR #55. `dev` contains the curated-catalog expansion, governance and promotion gates, focused model-management recovery, Android identity and the shared Compose design system. Repository-level protection for `dev` remains an administrative gate that cannot be applied from the code tree.

## Legacy model-management disposition

PR #53 is merged into `dev` and contains the focused verification, confirmation, removal
protection, metadata cleanup and privacy-safe error behavior. PR #34 is closed as superseded;
its branch is historical audit material only and must not be merged or revived as a product path.
Remote branch deletion remains an administrative cleanup after the recorded audit.

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

### Open runtime residency controls

The runtime already loads and unloads models through opaque handles, reuses the compatible loaded
model, protects active sessions and queued work from unsafe unload, and can release idle resources
for Android background or memory-pressure signals. These operations do not delete the installed
GGUF from `ModelStore`.

The product-facing `Load in memory` / `Unload from memory` controls and the configurable warm idle
TTL remain open. The TTL must start only after the final context is released, cancel or rearm on
reuse, recheck active and queued ownership before eviction, and preserve the installed artifact,
binding and selected-model metadata. The active implementation and device-validation backlog is
tracked as RT-01 in [`dev-integration-and-harness-0.5-plan.md`](dev-integration-and-harness-0.5-plan.md).

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

Status: **DONE through PR #53**.

PR #53 was squash-merged into `dev` as `9451314`. The connected-phone implementation includes
explicit verification, confirmation, loaded-model removal protection, metadata cleanup and
privacy-safe deterministic tests without restoring the obsolete standalone console.

### Block 8 — product and hardware completion

- complete ViewModel/UDF and Navigation Compose detail routes;
- complete explicit non-destructive model load/unload controls and the configurable warm idle TTL;
- add Compose UI, screenshot, accessibility and responsive tests;
- validate catalog download and installation on representative physical devices;
- execute the complete real-GGUF production-readiness gate;
- record privacy-safe release evidence.

The current local integration candidate provides the connected phone app with compact branded
chrome, Navigation Compose top-level routing, bottom navigation or navigation rail according
to width and all five primary surfaces. A subsequent visual-matching pass replaces generic
Material sizing and large hero cards with the mockups' dense typography, restrained radii,
thin panels, list-oriented runtime and health states, compact action tiles, underline tabs,
unboxed bottom-navigation selection and the runtime hexagon visual. Real empty, unavailable
and not-run states remain explicit instead of displaying the populated illustrative values from
the mockups. Instrumented checks cover shell height and destination reachability. This is
partial completion of the block: the candidate still requires PR review and cumulative remote
CI; controller state still needs migration to ViewModel/UDF; detail routes, screenshot and
accessibility matrices remain; emulator evidence does not replace the required physical-device
gate. The detailed executable backlog is maintained in
[`dev-integration-and-harness-0.5-plan.md`](dev-integration-and-harness-0.5-plan.md).

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
