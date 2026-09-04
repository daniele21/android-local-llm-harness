# Observability — Coding Agent Guide

## Scope

This guide applies to `observability/**` and supplements the repository-wide [`AGENTS.md`](../AGENTS.md). It covers stable telemetry contracts, the separate inference-audit contracts/test store, bounded in-memory and Room persistence, health checks, Android resource snapshots and benchmark baselines/regressions.

Normal diagnostics/telemetry are best-effort and must not fail or corrupt inference. The ADR-0017 inference-audit domain is intentionally different: Harnex production composition uses strict durable admission/prepared/terminal commits so an accepted inference cannot silently become unaudited.

## Navigation

| Concern | Code owner | Read first |
| --- | --- | --- |
| Run, log, health, resource, retention or benchmark schema | `contracts` | [`architecture.md`](../docs/architecture.md), [`harness-telemetry-composition.md`](../docs/harness-telemetry-composition.md) |
| Sensitive inference-audit schema/lifecycle | `contracts` package `io.github.daniele21.localllm.audit` | [ADR 0017](../docs/adr/0017-durable-local-inference-audit.md), [`local-inference-activity-audit.md`](../docs/features/local-inference-activity-audit.md) |
| Deterministic bounded test/ephemeral storage | `in-memory-store` | Contract tests, audit tests and [`console-observability.md`](../docs/console-observability.md) |
| Room entities, queries, retention or migrations | `room-store` | [ADR 0001](../docs/adr/0001-room-backed-telemetry.md), ADR 0017, Room tests |
| Health orchestration and checks | `health-engine` | [`health-engine.md`](../docs/health-engine.md), [`harness-health-composition.md`](../docs/harness-health-composition.md) |
| Android PSS, heap, memory and thermal snapshots | `android-resource-probe` | [`resource-observability.md`](../docs/resource-observability.md), [`harness-resource-composition.md`](../docs/harness-resource-composition.md) |
| Baselines, history and regression evaluation | `benchmark-engine` | [`benchmark-engine/README.md`](benchmark-engine/README.md), [`benchmark-engine.md`](../docs/benchmark-engine.md), [`harness-benchmark-composition.md`](../docs/harness-benchmark-composition.md) |
| Connected presentation | Consumer app sources | [`harness-logs-composition.md`](../docs/harness-logs-composition.md) and the [phone-test guide](../apps/local-llm-phone-test/AGENTS.md) |

Dependency direction:

```text
core/contracts
    -> observability/contracts
        -> in-memory-store / room-store / android-resource-probe
        -> health-engine
            -> benchmark-engine
        -> runtime and app consumers through neutral contracts
```

Before editing a schema or repository method, find every implementation, fake and presenter:

```bash
rg '<contract-or-method>' observability core apps integrations
rg --files observability/<module>/src/main observability/<module>/src/test
rg -n 'project\(' observability core apps integrations --glob 'build.gradle.kts'
```

## Local invariants

- Normal telemetry contains identifiers, statuses, typed codes, bounded fields and measurements; it excludes prompts, generated output and arbitrary exception text.
- Sensitive prompt/effective-prompt/output/reasoning persistence is allowed only through the ADR-0017 audit domain; never add it to `TelemetryRepository`, `GenerationRunRecord`, `StructuredLog`, health results, benchmark rows or normal exports.
- Every collection and query has an explicit bound. Telemetry retention and audit retention remain independently defined.
- Telemetry persistence is best-effort and isolated from inference success, cancellation and cleanup.
- Audit production persistence has explicit strict commit results; storage/key failure must be representable without arbitrary exception text and must not silently fall back to unaudited success.
- Store implementations preserve the semantics of their neutral contracts; fakes must not silently diverge from Room lifecycle/query behavior.
- Room work stays off the Android main thread, uses app-private storage and receives non-destructive, tested migrations.
- Audit sensitive blobs are encrypted before Room persistence; plaintext metadata is minimal and never includes prompt/output content.
- Audit lifecycle transitions are monotonic/idempotent for exact replays, terminal states never regress, and non-terminal restart reconciliation is explicit.
- Health checks are observable and explicit. Unknown checks return `NOT_RUN`; one failing check must not abort unrelated checks.
- Resource capture is caller-driven. Missing measurements remain nullable/unavailable rather than invented zeroes.
- Cold, warm and unknown load classifications remain distinct; `UNKNOWN` cannot become a benchmark baseline.
- Benchmark history is immutable evidence; viewing history does not change the active baseline.
- App UIs depend on neutral contracts/presenters, not Room entities, ciphertext, Keystore objects or another application's private database.
- Shared reports and logs exclude document URIs, signed URLs, private backing paths, device serial numbers and decrypted audit content.

## Change routing

- Start schema/API changes in `contracts`; update every implementation, fake, engine and presenter in the same slice.
- Keep telemetry and audit contracts separate even when they share a Gradle module; `requestId` is the correlation key, not a reason to merge privacy/failure semantics.
- Start persistence behavior in the matching store; keep domain decisions out of Room entities and DAOs.
- Start audit encryption/retention/transition behavior behind `InferenceAuditRepository`; UI and Binder adapters must not own it.
- Start check orchestration in `health-engine`; keep runtime-specific observations behind injected probes/clients.
- Start metric capture in the producing runtime/resource probe and stable contracts before changing presentation.
- Start regression mathematics and baseline policy in `benchmark-engine`; keep UI mapping read-only.
- When telemetry or audit affects a UI, preserve empty, unavailable, stale, degraded, decrypt-failure and source-failure states without fabricating data.

## Validation

For changes confined to observability implementations:

```bash
./gradlew spotlessCheck
./gradlew --no-configuration-cache detekt verifyNoModelArtifacts
./gradlew :observability:contracts:testDebugUnitTest \
  :observability:in-memory-store:testDebugUnitTest \
  :observability:room-store:testDebugUnitTest \
  :observability:health-engine:testDebugUnitTest \
  :observability:android-resource-probe:testDebugUnitTest \
  :observability:benchmark-engine:testDebugUnitTest
```

For `observability/contracts` changes, use the repository selector because runtime, stores, engines and app test doubles are consumers. For Room schema changes, also run:

```bash
./gradlew :observability:room-store:assembleDebugAndroidTest
```

Keep exported Room schemas under `room-store/src/androidTest/assets` and cover every supported migration with `MigrationTestHelper`, including historical rows and nullable/new-field semantics. Audit migration tests additionally prove encrypted content remains decryptable or fails with a typed corruption/key result; they never assert plaintext database content.

Run `connectedDebugAndroidTest` only when an emulator or device is available; successful assembly proves compilation and packaging, not migration execution. Compile/test `apps/local-llm-phone-test` when presentation or app adapters change. Physical-device evidence is required only before hardware-backed-key, memory, thermal or representative performance claims.

## Maintaining this guide

Update this file in the same change when:

- an observability/audit module or responsibility is added, removed, renamed or moved;
- a stable schema gains or loses implementers or direct consumers;
- privacy, redaction, bounds, retention, encryption or failure-isolation rules change;
- Room schema/migration policy or threading ownership changes;
- health, resource, benchmark or audit composition changes;
- targeted Gradle tasks or physical evidence requirements change.

Update the root guide for repository-wide contract fan-out or changed module ownership. Update architecture/ADR documentation for durable persistence, privacy or dependency decisions. Update the focused behavior document when semantics change, and current state/roadmap only when implementation or evidence status changes.

After editing, run from the repository root:

```bash
python3 scripts/verify-agent-navigation.py
```
