# Model Lifecycle — Coding Agent Guide

## Scope

This guide applies to `models/**` and supplements the repository-wide [`AGENTS.md`](../AGENTS.md). It covers model identity and profiles, persistent Host Control Plane configuration, installed artifact storage, administrator catalogs, secure transfer and explicit verified installation.

Read the root guide first. A model change must preserve the separation between selection, transfer, installation, binding and runtime activation.

## Navigation

Read only the sources matching the change:

| Concern | Code owner | Read first |
| --- | --- | --- |
| Artifact, load profile, use case or app binding | `models/model-profile` | [`architecture.md`](../docs/architecture.md), [`api-usage.md`](../docs/api-usage.md) |
| Persistent applications/use cases/presets/bindings/exposure | `models/control-plane-room-store` | [`shared-runtime/control-plane/workstreams/configuration.md`](../docs/shared-runtime/control-plane/workstreams/configuration.md), [ADR 0015](../docs/adr/0015-harness-managed-control-plane.md) |
| Installed SHA-256 object, import, verification or removal | `models/model-store` | [`model-installation.md`](../docs/model-installation.md), [`api-usage.md`](../docs/api-usage.md) |
| Catalog schema, validation, targeting or compatibility | `models/model-catalog` | [`model-catalog/README.md`](model-catalog/README.md), [`curated-model-catalog.md`](../docs/curated-model-catalog.md), [ADR 0005](../docs/adr/0005-admin-model-catalog-boundaries.md) |
| URI/address policy, download lifecycle or verified holding | `models/model-download` | [`model-download/README.md`](model-download/README.md), [`secure-model-download.md`](../docs/secure-model-download.md), [ADR 0006](../docs/adr/0006-secure-model-download-core.md) |
| Catalog/profile reconciliation, inspection or publication | `models/model-install` | [`model-installation.md`](../docs/model-installation.md), [ADR 0007](../docs/adr/0007-explicit-verified-download-installation.md) |
| Connected phone orchestration and UI | Consumer under `apps/local-llm-phone-test` | [`model-management-phone.md`](../docs/model-management-phone.md) and the [phone-test guide](../apps/local-llm-phone-test/AGENTS.md) |

The lifecycle and dependency direction are:

```text
model-catalog
    -> model-download
    -> opaque VerifiedDownloadHandle
    -> model-install
    -> GGUF metadata inspection through a neutral contract
    -> model-store publication and verification
    -> explicit application/use-case binding
    -> runtime preparation
```

Host Control Plane persistence has a separate dependency direction:

```text
models/model-profile domain contracts
    -> models/control-plane-room-store
    -> Android Room database
```

Search the contract type before changing a data class or interface, then inspect implementations and consumers:

```bash
rg '<type-or-method>' models core backends apps observability transports
rg --files models/<module>/src/main models/<module>/src/test
rg -n 'project\(' models backends apps --glob 'build.gradle.kts'
```

## Local invariants

- SHA-256 is the immutable physical identity; URLs, filenames, catalog IDs and profile IDs are not substitutes.
- Product eligibility is limited to Qwen3.5 dense 0.8B/2B under [`ADR 0011`](../docs/adr/0011-qwen35-only-product-support.md); keep the underlying model contracts neutral, treat import labels as untrusted and retain unsupported installed bytes until explicit user removal.
- `models/model-profile` describes configuration and binding; it does not own filesystem, Room or network behavior.
- `models/control-plane-room-store` is the Android persistence adapter for Host Control Plane configuration. It preserves immutable revision identity, referential integrity and transactional replacement without owning runtime/model loading.
- `models/model-store` owns installed artifacts. Imports stream through staging, publish atomically and never expose a partial artifact as ready.
- `models/model-catalog` owns validated release metadata and compatibility policy, not HTTP transfer or runtime selection.
- `models/model-download` owns secure transfer and verified holding, not GGUF inspection, store import, binding or runtime activation.
- Verified backing paths remain private. Consumers receive an opaque handle and immutable expected identity.
- `models/model-install` validates exact catalog/profile/target agreement, inspects before import, publishes through `ModelStore` and verifies after import.
- Installation success does not select a model, change a binding, load the runtime or start inference.
- Cancellation, retry, restart cleanup and partial installation failure must be explicit, deterministic and non-destructive.
- Signed URLs, authorization data, document URIs, private paths, model bytes, prompts and output must not enter normal logs, metadata or shared reports.
- Do not add network or Android UI dependencies to model domain modules merely for application convenience.

## Change routing

- Start identity or binding changes in `models/model-profile`; compile runtime and app registry consumers.
- Start Host Control Plane persistence/schema changes in `models/control-plane-room-store`; preserve the `HostControlPlaneStore` contract, transactional state integrity, restart persistence and non-destructive migration behavior.
- Start import, deduplication, verification and removal changes in `models/model-store`; test interruption, conflicts, active-model protection and integrity failure.
- Start catalog wire/schema changes in `models/model-catalog`; preserve bounded fail-closed decoding, revision rules and last-good state.
- Start source, redirect, DNS/address, size, storage or retry changes in `models/model-download`; preserve URL redaction and terminal partial-file cleanup.
- Start installation sequencing in `models/model-install`; inspect backend adapters and phone orchestration without moving policy into either consumer.
- When a change spans two lifecycle stages, keep the stages explicit and connect them through contracts rather than merging their ownership.

## Validation

Run formatting, static analysis and the tests for every changed model stage:

```bash
./gradlew spotlessCheck
./gradlew --no-configuration-cache detekt verifyNoModelArtifacts
./gradlew :models:model-profile:testDebugUnitTest \
  :models:control-plane-room-store:testDebugUnitTest \
  :models:control-plane-room-store:assembleDebugAndroidTest \
  :models:model-store:testDebugUnitTest \
  :models:model-catalog:testDebugUnitTest \
  :models:model-download:testDebugUnitTest \
  :models:model-install:testDebugUnitTest
```

For catalog, download or installation contract changes, also compile and test the connected consumer and inspector adapter:

```bash
./gradlew :backends:llama-cpp:testDebugUnitTest \
  :apps:local-llm-phone-test:compileDebugKotlin \
  :apps:local-llm-phone-test:testDebugUnitTest
```

Add module lint, packaging and the repository-wide gate when Android configuration, shared contracts or multiple domains change. Physical-device download/install evidence remains required before compatibility or production-readiness claims.

## Maintaining this guide

Update this file in the same change when:

- a model module is added, removed, renamed or given a different responsibility;
- lifecycle order or dependency direction changes;
- a contract moves between profile, control-plane persistence, store, catalog, download and install;
- privacy, path-opacity, atomicity, retry or cleanup guarantees change;
- the targeted Gradle tasks or direct consumers change;
- a focused model document or ADR becomes canonical or is superseded.

Update the root repository map and routing only when the externally visible module boundary changes. Update architecture and add or supersede an ADR before documenting a new durable lifecycle boundary here. Keep release status and current backlog out of this guide; those belong in the current-state ledger and roadmap.

After editing, run from the repository root:

```bash
python3 scripts/verify-agent-navigation.py
```
